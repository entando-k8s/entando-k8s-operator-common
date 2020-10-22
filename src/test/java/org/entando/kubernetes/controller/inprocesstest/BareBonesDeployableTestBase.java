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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.barebones.BareBonesContainer;
import org.entando.kubernetes.controller.common.examples.barebones.BareBonesDeployable;
import org.entando.kubernetes.controller.common.examples.barebones.BarebonesDeploymentResult;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.PortSpec;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.controller.test.support.PodBehavior;
import org.entando.kubernetes.controller.test.support.VariableReferenceAssertions;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.junit.jupiter.api.Test;

public abstract class BareBonesDeployableTestBase implements InProcessTestUtil, PodBehavior, FluentTraversals, VariableReferenceAssertions {

    public static final String SAMPLE_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("sample-namespace");
    public static final String SAMPLE_NAME = EntandoOperatorTestConfig.calculateName("sample-name");
    public static final String SAMPLE_NAME_DB = KubeUtils.snakeCaseOf(SAMPLE_NAME + "_db");
    EntandoPlugin plugin = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    protected SimpleK8SClient k8sClient;

    private SampleController<EntandoPlugin, BarebonesDeploymentResult> controller;

    @Test
    void testBasicDeploymentWithAdditionalPorts() {
        //Given I have a controller that processes EntandoPlugins
        this.k8sClient = getClient();
        controller = new SampleController<EntandoPlugin, BarebonesDeploymentResult>(k8sClient, mock(SimpleKeycloakClient.class)) {
            @Override
            protected Deployable<BarebonesDeploymentResult, EntandoPlugin> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new BareBonesDeployable<>(newEntandoPlugin, new BareBonesContainer() {
                    @Override
                    public List<PortSpec> getAdditionalPorts() {
                        return Collections.singletonList(new PortSpec("ping", 8888));
                    }
                });
            }

        };
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin, plugin.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin.getClass(), plugin.getMetadata().getNamespace(), plugin.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect one deployment. This is where we can put all the assertions
        Deployment serverDeployment = k8sClient.deployments()
                .loadDeployment(plugin, SAMPLE_NAME + "-db-deployment");
        verifyThatAllVariablesAreMapped(plugin, k8sClient, serverDeployment);
        verifyThatAllVolumesAreMapped(plugin, k8sClient, serverDeployment);
        assertThat(thePortNamed("my-db-port").on(theContainerNamed(BareBonesContainer.NAME_QUALIFIER + "-container").on(serverDeployment))
                .getContainerPort(), is(5432));
        assertThat(thePortNamed("ping").on(theContainerNamed(BareBonesContainer.NAME_QUALIFIER + "-container").on(serverDeployment))
                .getContainerPort(), is(8888));
    }

    protected final void emulatePodWaitingBehaviour(EntandoCustomResource resource, String deploymentName) {
        new Thread(() -> {
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Deployment serverDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-db-deployment");
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    public <T extends EntandoBaseCustomResource> void onAdd(T resource) {
        new Thread(() -> {
            T createResource = getClient().entandoResources().createOrPatchEntandoResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .addNewParameter("MY_VAR", "MY_VAL")
                .withDbms(DbmsVendor.EMBEDDED).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
    }

}