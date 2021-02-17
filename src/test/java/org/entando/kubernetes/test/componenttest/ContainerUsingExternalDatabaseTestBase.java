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

package org.entando.kubernetes.test.componenttest;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.examples.SampleController;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.command.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class ContainerUsingExternalDatabaseTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior,
        VariableReferenceAssertions, CommonLabels {

    public static final String SAMPLE_NAMESPACE = "sample-namespace";
    public static final String SAMPLE_NAME = "sample-name";
    public static final String SAMPLE_NAME_DB = NameUtils.snakeCaseOf(SAMPLE_NAME + "_db");
    final EntandoPlugin plugin1 = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    private SampleController<EntandoPluginSpec, EntandoPlugin, SampleExposedDeploymentResult> controller;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @BeforeEach
    public void enableQueueing() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
    }

    @AfterEach
    public void shutDown() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        scheduler.shutdownNow();
        getClient().pods().getPodWatcherQueue().clear();
    }

    @Test
    void testSelectingOneOfTwoExternalDatabase() {
        //Given I have a controller that processes EntandoPlugins
        controller = new SampleController<>(getClient(), getKeycloakClient()) {
            @Override
            protected SpringBootDeployable<EntandoPluginSpec> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SpringBootDeployable<>(newEntandoPlugin, keycloakConnectionConfig, databaseServiceResult);
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        emulateKeycloakDeployment(getClient());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //And we have two ExternalDatabases: one MySQL and one PostgreSQL
        createExternalDatabaseService(DbmsVendor.MYSQL, "10.0.0.123");
        createExternalDatabaseService(DbmsVendor.POSTGRESQL, "10.0.0.124");
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                getClient().entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a server deployment
        Deployment serverDeployment = getClient().deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), Matchers.is(1));
        verifySpringDatasource(serverDeployment);
        //Then  no db deployment
        assertNull(getClient().deployments().loadDeployment(plugin1, SAMPLE_NAME + "-db-deployment"));

        //And I an ingress paths
        Ingress ingress = getClient().ingresses().loadIngress(plugin1.getMetadata().getNamespace(),
                ((EntandoCustomResource) plugin1).getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX);
        assertThat(theHttpPath(SampleSpringBootDeployableContainer.MY_WEB_CONTEXT).on(ingress).getBackend().getServicePort().getIntVal(),
                Matchers.is(8084));
    }

    private void createExternalDatabaseService(DbmsVendor mysql, String s) {
        String secretName = mysql.name().toLowerCase() + "-secret";
        EntandoDatabaseService databaseService = new EntandoDatabaseServiceBuilder()
                .withNewMetadata().withName(SAMPLE_NAME + "-" + mysql.name().toLowerCase()).withNamespace(SAMPLE_NAMESPACE)
                .endMetadata()
                .withNewSpec().withDbms(mysql).withDatabaseName(SAMPLE_NAME_DB).withHost(s)
                .withSecretName(secretName).endSpec()
                .build();
        getClient().entandoResources().createOrPatchEntandoResource(databaseService);
        getClient().secrets().createSecretIfAbsent(databaseService,
                new SecretBuilder().withNewMetadata().withNamespace(SAMPLE_NAMESPACE).withName(secretName).endMetadata()
                        .addToData(SecretUtils.USERNAME_KEY, "username").addToData(SecretUtils.PASSSWORD_KEY, "asdf123").build());
        new CreateExternalServiceCommand(databaseService).execute(getClient());
    }

    protected abstract SimpleKeycloakClient getKeycloakClient();

    void verifySpringDatasource(Deployment serverDeployment) {
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_URL.name())
                .on(thePrimaryContainerOn(serverDeployment)), is(
                "jdbc:postgresql://" + SAMPLE_NAME + "-postgresql-db-service." + SAMPLE_NAMESPACE + ".svc.cluster.local:5432/"
                        + SAMPLE_NAME_DB));
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
        scheduler.schedule(() -> {
            try {
                //Deleting previous dbPreparationPods doesn't require events
                getClient().pods().getPodWatcherQueue().take();
                PodWatcher dbPodWatcher = getClient().pods().getPodWatcherQueue().take();
                await().atMost(20, TimeUnit.SECONDS).until(() -> getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), dbPreparationJobLabels(resource, "server")) != null);
                Pod dbPreparationPod = getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), dbPreparationJobLabels(resource, "server"));
                dbPodWatcher.eventReceived(Action.MODIFIED, podWithSucceededStatus(dbPreparationPod));
                PodWatcher serverPodWatcher = getClient().pods().getPodWatcherQueue().take();
                Deployment serverDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-server-deployment");
                serverPodWatcher.eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        }, 100, TimeUnit.MILLISECONDS);
    }

    public <S extends Serializable, T extends EntandoBaseCustomResource<S>> void onAdd(T resource) {
        scheduler.schedule(() -> {
            T createResource = getClient().entandoResources().createOrPatchEntandoResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }, 10, TimeUnit.MILLISECONDS);
    }

}
