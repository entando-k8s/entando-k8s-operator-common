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

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.PortSpec;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.examples.SampleController;
import org.entando.kubernetes.controller.spi.examples.barebones.BareBonesContainer;
import org.entando.kubernetes.controller.spi.examples.barebones.BareBonesDeployable;
import org.entando.kubernetes.controller.spi.examples.barebones.BarebonesDeploymentResult;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

//Because Sonar doesn't pick up that this class is subclassed in other packages
@SuppressWarnings({"java:S5786"})
public abstract class BareBonesDeployableTestBase implements InProcessTestUtil, PodBehavior, FluentTraversals, VariableReferenceAssertions {

    public static final String SAMPLE_NAMESPACE = "sample-namespace";
    public static final String SAMPLE_NAME = "sample-name";
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final EntandoPlugin plugin = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    protected SimpleK8SClient<?> k8sClient;
    private SampleController<EntandoPluginSpec, EntandoPlugin, BarebonesDeploymentResult> controller;
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
    void testBasicDeploymentWithAdditionalPorts() {
        //Given I have a controller that processes EntandoPlugins
        this.k8sClient = getClient();
        emulateKeycloakDeployment(k8sClient);
        controller = new SampleController<>(k8sClient,
                mock(SimpleKeycloakClient.class)) {
            @Override
            protected Deployable<BarebonesDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
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

        await().ignoreExceptions().atMost(20, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin.getClass(), plugin.getMetadata().getNamespace(), plugin.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect one deployment.
        Deployment serverDeployment = k8sClient.deployments()
                .loadDeployment(plugin, format("%s-%s-deployment", SAMPLE_NAME, BareBonesDeployable.NAME_QUALIFIER));
        verifyThatAllVariablesAreMapped(plugin, k8sClient, serverDeployment);
        verifyThatAllVolumesAreMapped(plugin, k8sClient, serverDeployment);
        assertThat(thePortNamed("my-db-port").on(theContainerNamed(BareBonesContainer.NAME_QUALIFIER + "-container").on(serverDeployment))
                .getContainerPort(), is(5432));
        assertThat(thePortNamed("ping").on(theContainerNamed(BareBonesContainer.NAME_QUALIFIER + "-container").on(serverDeployment))
                .getContainerPort(), is(8888));
    }

    @Test
    void testBasicDeploymentWithAllHealthProbes() {
        //Given I have a controller that processes EntandoPlugins that has a maximumStartupTimeSeconds of 60
        this.k8sClient = getClient();
        emulateKeycloakDeployment(k8sClient);
        controller = new SampleController<>(k8sClient,
                mock(SimpleKeycloakClient.class)) {
            @Override
            protected Deployable<BarebonesDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new BareBonesDeployable<>(newEntandoPlugin, new BareBonesContainer() {
                    @Override
                    public Optional<Integer> getMaximumStartupTimeSeconds() {
                        return Optional.of(60);
                    }
                });
            }

        };
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin, plugin.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin);

        await().ignoreExceptions().atMost(20, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin.getClass(), plugin.getMetadata().getNamespace(), plugin.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect one deployment.
        Deployment serverDeployment = k8sClient.deployments()
                .loadDeployment(plugin, format("%s-%s-deployment", SAMPLE_NAME, BareBonesDeployable.NAME_QUALIFIER));
        final Container thePrimaryContainer = thePrimaryContainerOn(serverDeployment);
        //With a stratup probe
        final Probe startupProbe = thePrimaryContainer.getStartupProbe();
        assertThat(startupProbe.getInitialDelaySeconds(), Is.is(nullValue()));
        assertThat(startupProbe.getSuccessThreshold(), Is.is(nullValue()));
        assertThat(startupProbe.getFailureThreshold(), Is.is(11));
        //That executes 10 times in the allowed maximumStartupTime (60)
        assertThat(startupProbe.getPeriodSeconds(), Is.is(60 / 10));
        assertThat(startupProbe.getTimeoutSeconds(), Is.is(5));
        //And a ReadinessProbe
        final Probe readinessProbe = thePrimaryContainer.getReadinessProbe();
        //That executes immediately after the first succeeding startupProbe
        assertThat(readinessProbe.getInitialDelaySeconds(), Is.is(nullValue()));
        assertThat(readinessProbe.getSuccessThreshold(), Is.is(nullValue()));
        assertThat(readinessProbe.getFailureThreshold(), Is.is(1));
        assertThat(readinessProbe.getPeriodSeconds(), Is.is(10));
        assertThat(readinessProbe.getTimeoutSeconds(), Is.is(5));
        //And a livenessprbe
        final Probe livenessProbe = thePrimaryContainer.getLivenessProbe();
        //That executes immediately after the first succeeding startupProbe
        assertThat(livenessProbe.getInitialDelaySeconds(), Is.is(nullValue()));
        assertThat(livenessProbe.getSuccessThreshold(), Is.is(nullValue()));
        assertThat(livenessProbe.getFailureThreshold(), Is.is(1));
        assertThat(livenessProbe.getPeriodSeconds(), Is.is(10));
        assertThat(livenessProbe.getTimeoutSeconds(), Is.is(3));
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void stashNamespacesToObserve() {
        this.properties.putAll((Map) System.getProperties());
    }

    @AfterEach
    public void unstashNamespacesToObserve() {
        System.getProperties().putAll(this.properties);
    }

    @Test
    void testBasicDeploymentWithClusterScopedRoles() {
        //Given I have a controller that processes EntandoPlugins
        this.k8sClient = getClient();
        emulateKeycloakDeployment(k8sClient);
        controller = new SampleController<>(k8sClient, mock(SimpleKeycloakClient.class)) {
            @Override
            protected Deployable<BarebonesDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new BareBonesDeployable<>(newEntandoPlugin, new BareBonesContainer());
            }

        };
        //And the operator is deployed at cluster scope
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty(), "*");
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin, plugin.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin.getClass(), plugin.getMetadata().getNamespace(), plugin.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a namespace scoped RoleBinding
        RoleBinding editorRoleBinding = this.k8sClient.serviceAccounts().loadRoleBinding(plugin, "my-service-account-entando-editor");
        assertThat(editorRoleBinding, notNullValue());
        //Then that binds to the entando-editor ClusterRole
        assertThat(editorRoleBinding.getRoleRef().getKind(), is("ClusterRole"));
        assertThat(editorRoleBinding.getRoleRef().getName(), is("entando-editor"));
        assertThat(editorRoleBinding.getSubjects().get(0).getName(), is("my-service-account"));
        assertThat(editorRoleBinding.getSubjects().get(0).getKind(), is("ServiceAccount"));
        assertThat(editorRoleBinding.getSubjects().get(0).getNamespace(), is(SAMPLE_NAMESPACE));
        //And another namespace scoped RoleBinding
        RoleBinding viewRoleBinding = this.k8sClient.serviceAccounts().loadRoleBinding(plugin, "my-service-account-pod-viewer");
        assertThat(viewRoleBinding, notNullValue());
        //Then that binds to the pod-viewer ClusterRole
        assertThat(viewRoleBinding.getRoleRef().getKind(), is("ClusterRole"));
        assertThat(viewRoleBinding.getRoleRef().getName(), is("pod-viewer"));
        assertThat(viewRoleBinding.getSubjects().get(0).getName(), is("my-service-account"));
        assertThat(viewRoleBinding.getSubjects().get(0).getKind(), is("ServiceAccount"));
        assertThat(viewRoleBinding.getSubjects().get(0).getNamespace(), is(SAMPLE_NAMESPACE));
    }

    protected final <S extends EntandoDeploymentSpec> void emulatePodWaitingBehaviour(EntandoBaseCustomResource<S> resource,
            String deploymentName) {
        scheduler.schedule(() -> {
            try {
                final PodWatcher podWatcher = getClient().pods().getPodWatcherQueue().take();
                Deployment serverDeployment = getClient().deployments()
                        .loadDeployment(resource, format("%s-%s-deployment", deploymentName, BareBonesDeployable.NAME_QUALIFIER));
                podWatcher.eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        }, 200, TimeUnit.MILLISECONDS);
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

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .addToEnvironmentVariables("MY_VAR", "MY_VAL")
                .withDbms(DbmsVendor.EMBEDDED).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
    }

}
