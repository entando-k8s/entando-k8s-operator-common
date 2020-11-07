/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.KubeUtils.standardIngressName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.ExposedDeploymentResult;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.common.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.controller.test.support.PodBehavior;
import org.entando.kubernetes.controller.test.support.VariableReferenceAssertions;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S5786")
public abstract class SpringBootContainerTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior, VariableReferenceAssertions {

    public static final String SAMPLE_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("sample-namespace");
    public static final String SAMPLE_NAME = EntandoOperatorTestConfig.calculateName("sample-name");
    public static final String SAMPLE_NAME_DB = KubeUtils.snakeCaseOf(SAMPLE_NAME + "_db");
    EntandoPlugin plugin1 = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    private SampleController<EntandoPlugin, EntandoPluginSpec, ExposedDeploymentResult> controller;

    @Test
    void testBasicDeployment() {
        //Given I have a controller that processes EntandoPlugins
        controller = new SampleController<EntandoPlugin, EntandoPluginSpec, ExposedDeploymentResult>(getClient(), getKeycloakClient()) {
            @Override
            protected Deployable<ExposedDeploymentResult, EntandoPluginSpec> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SpringBootDeployable<>(newEntandoPlugin, keycloakConnectionConfig, databaseServiceResult);
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        emulateKeycloakDeployment(getClient());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                getClient().entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a server deployment
        Deployment serverDeployment = getClient().deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), Matchers.is(1));
        verifyThatAllVariablesAreMapped(plugin1, getClient(), serverDeployment);
        verifyThatAllVolumesAreMapped(plugin1, getClient(), serverDeployment);
        verifySpringDatasource(serverDeployment);
        verifySpringSecuritySettings(thePrimaryContainerOn(serverDeployment), SAMPLE_NAME + "-server-secret");
        //Then  a db deployment
        Deployment dbDeployment = getClient().deployments().loadDeployment(plugin1, SAMPLE_NAME + "-db-deployment");
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));

        //And I an ingress paths
        Ingress ingress = getClient().ingresses().loadIngress(plugin1.getMetadata().getNamespace(), standardIngressName(plugin1));
        assertThat(theHttpPath(SampleSpringBootDeployableContainer.MY_WEB_CONTEXT).on(ingress).getBackend().getServicePort().getIntVal(),
                Matchers.is(8084));
    }

    protected abstract SimpleKeycloakClient getKeycloakClient();

    public void verifySpringDatasource(Deployment serverDeployment) {
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KubeUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_URL.name())
                .on(thePrimaryContainerOn(serverDeployment)), is(
                "jdbc:postgresql://" + SAMPLE_NAME + "-db-service." + SAMPLE_NAMESPACE + ".svc.cluster.local:5432/" + SAMPLE_NAME_DB));
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(serverDeployment)), is("MY_VAL"));
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(serverDeployment)), is("MY_VAL"));
    }

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .addToEnvironmentVariables("MY_VAR", "MY_VAL")
                .withDbms(DbmsVendor.POSTGRESQL).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
    }

    protected final <S extends EntandoDeploymentSpec> void emulatePodWaitingBehaviour(EntandoBaseCustomResource<S> resource,
            String deploymentName) {
        new Thread(() -> {
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Deployment dbDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-db-deployment");
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithReadyStatus(dbDeployment));
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                String dbJobName = String.format("%s-db-preparation-job", resource.getMetadata().getName());
                Pod dbPreparationPod = getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), KubeUtils.DB_JOB_LABEL_NAME, dbJobName);
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithSucceededStatus(dbPreparationPod));
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Deployment serverDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-server-deployment");
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoBaseCustomResource> void onAdd(T resource) {
        new Thread(() -> {
            T createResource = getClient().entandoResources().createOrPatchEntandoResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

    public <T extends EntandoBaseCustomResource> void onDelete(T resource) {
        new Thread(() -> {
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.DELETED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, resource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, resource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }
}
