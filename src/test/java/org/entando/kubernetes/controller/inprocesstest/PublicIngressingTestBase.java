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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.SampleDeployableContainer;
import org.entando.kubernetes.controller.common.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.common.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.KeycloakAware;
import org.entando.kubernetes.controller.spi.KubernetesPermission;
import org.entando.kubernetes.controller.test.support.CommonLabels;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.controller.test.support.PodBehavior;
import org.entando.kubernetes.controller.test.support.VariableReferenceAssertions;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings({"java:S6068", "java:S6073", "java:S5786"})
public abstract class PublicIngressingTestBase implements InProcessTestUtil, PodBehavior, FluentTraversals, VariableReferenceAssertions,
        CommonLabels {

    public static final String SAMPLE_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("sample-namespace");
    public static final String SAMPLE_NAME = EntandoOperatorTestConfig.calculateName("sample-name");
    public static final String OTHER_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("other-namespace");
    public static final String OTHER_NAME = EntandoOperatorTestConfig.calculateName("other-name");
    protected SimpleK8SClient<?> k8sClient;
    EntandoPlugin plugin1 = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    EntandoPlugin plugin2 = buildPlugin(OTHER_NAMESPACE, OTHER_NAME);
    SimpleKeycloakClient mock = Mockito.mock(SimpleKeycloakClient.class);
    private SampleController<EntandoPluginSpec, EntandoPlugin, SampleExposedDeploymentResult> controller;

    @BeforeEach
    void setIngressClass() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_INGRESS_CLASS.getJvmSystemProperty(), "nginx");
    }

    @AfterEach
    void removeIngressClass() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_INGRESS_CLASS.getJvmSystemProperty());
    }

    @Test
    void testTwoDeploymentsSharingAnIngress() {
        //Given I have a PublicIngressingDeployment in the Sample Namespace
        testBasicDeployment();
        //And I have a plugin in another namespace to share the same Ingress
        controller = new SampleController<>(k8sClient, mock) {

            @Override
            protected Deployable<SampleExposedDeploymentResult, EntandoPluginSpec> createDeployable(EntandoPlugin plugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SamplePublicIngressingDbAwareDeployable<>(plugin, databaseServiceResult,
                        keycloakConnectionConfig) {
                    @Override
                    public String getIngressNamespace() {
                        return SAMPLE_NAMESPACE;
                    }

                    @Override
                    public String getIngressName() {
                        return KubeUtils.standardIngressName(plugin1);
                    }

                    @Override
                    protected List<DeployableContainer> createContainers(EntandoBaseCustomResource<EntandoPluginSpec> entandoResource) {
                        return Collections.singletonList(new SampleDeployableContainer<>(entandoResource, databaseServiceResult) {
                            @Override
                            public int getPrimaryPort() {
                                return 8082;
                            }

                            @Override
                            public String getWebContextPath() {
                                return "/auth3";
                            }
                        });
                    }
                };
            }
        };
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin2, plugin2.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin2);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin2.getClass(), plugin2.getMetadata().getNamespace(), plugin2.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect two deployments. This is where we can put all the assertions
        Deployment serverDeployment = k8sClient.deployments()
                .loadDeployment(plugin2, OTHER_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        verifyThatAllVariablesAreMapped(plugin2, k8sClient, serverDeployment);
        verifyThatAllVolumesAreMapped(plugin2, k8sClient, serverDeployment);
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));
        Deployment dbDeployment = k8sClient.deployments().loadDeployment(plugin2, OTHER_NAME + "-db-deployment");
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));
        //And I expect three ingress paths
        Ingress ingress = k8sClient.ingresses().loadIngress(plugin1.getMetadata().getNamespace(), standardIngressName(plugin1));
        assertThat(theHttpPath("/auth").on(ingress).getBackend().getServicePort().getIntVal(), is(8080));
        assertThat(theHttpPath("/auth2").on(ingress).getBackend().getServicePort().getIntVal(), is(8081));
        assertThat(theHttpPath("/auth3").on(ingress).getBackend().getServicePort().getIntVal(), is(8082));

    }

    @Test
    void testBasicDeployment() {
        //Given I have a controller that processes EntandoPlugins
        lenient().when(mock.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn("ASDFASDFASDfa");
        this.k8sClient = getClient();
        controller = new SampleController<>(k8sClient, mock) {

            @Override
            protected Deployable<SampleExposedDeploymentResult, EntandoPluginSpec> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SamplePublicIngressingDbAwareDeployable<>(newEntandoPlugin, databaseServiceResult,
                        keycloakConnectionConfig) {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected List<DeployableContainer> createContainers(EntandoBaseCustomResource<EntandoPluginSpec> entandoResource) {
                        return Arrays.asList(new SampleDeployableContainer<>(entandoResource, databaseServiceResult),
                                new EntandoPluginSampleDeployableContainer(entandoResource, keycloakConnectionConfig,
                                        databaseServiceResult));
                    }
                };
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        emulateKeycloakDeployment(getClient());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                k8sClient.entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect two deployments. This is where we can put all the assertions
        Deployment serverDeployment = k8sClient.deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        verifyThatAllVariablesAreMapped(plugin1, k8sClient, serverDeployment);
        verifyThatAllVolumesAreMapped(plugin1, k8sClient, serverDeployment);
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(2));
        Deployment dbDeployment = k8sClient.deployments().loadDeployment(plugin1, SAMPLE_NAME + "-db-deployment");
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));
        //And I expect two ingress paths
        Ingress ingress = k8sClient.ingresses().loadIngress(plugin1.getMetadata().getNamespace(), standardIngressName(plugin1));
        assertThat(theHttpPath("/auth").on(ingress).getBackend().getServicePort().getIntVal(), is(8080));
        assertThat(theHttpPath("/auth2").on(ingress).getBackend().getServicePort().getIntVal(), is(8081));
        assertThat(ingress.getMetadata().getAnnotations().get("kubernetes.io/ingress.class"), is("nginx"));
        assertThat(ingress.getMetadata().getAnnotations().get("nginx.ingress.kubernetes.io/proxy-body-size"), is("50m"));
    }

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .withDbms(DbmsVendor.POSTGRESQL).withReplicas(2).withIngressHostName("myhost.name.com")
                .withNewResourceRequirements()
                .withFileUploadLimit("50m")
                .endResourceRequirements()
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
                //wait for deletion of db preparation pod
                await().pollInterval(1, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                        .until(() -> getClient().pods().getPodWatcherHolder().getAndSet(null) != null);
                //wait for db preparation pod
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Pod dbPreparationPod = getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), dbPreparationJobLabels(resource, "server"));
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
    public <S extends Serializable, T extends EntandoBaseCustomResource<S>> void onAdd(T resource) {
        new Thread(() -> {
            T createResource = k8sClient.entandoResources().createOrPatchEntandoResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

    private static class EntandoPluginSampleDeployableContainer extends SampleDeployableContainer<EntandoPluginSpec> implements
            KeycloakAware {

        private KeycloakConnectionConfig keycloakConnectionConfig;

        public EntandoPluginSampleDeployableContainer(EntandoBaseCustomResource<EntandoPluginSpec> entandoResource,
                KeycloakConnectionConfig keycloakConnectionConfig,
                DatabaseServiceResult databaseServiceResult) {
            super(entandoResource, databaseServiceResult);
            this.keycloakConnectionConfig = keycloakConnectionConfig;
        }

        @Override
        public int getPrimaryPort() {
            return 8081;
        }

        @Override
        public String getWebContextPath() {
            return "/auth2";
        }

        @Override
        public List<KubernetesPermission> getKubernetesPermissions() {
            return Arrays.asList(new KubernetesPermission("entando.org", "EntandoPlugin", "CREATE", "DELETE"));
        }

        @Override
        public KeycloakConnectionConfig getKeycloakConnectionConfig() {
            return keycloakConnectionConfig;
        }

        @Override
        public KeycloakClientConfig getKeycloakClientConfig() {
            return new KeycloakClientConfig(determineRealm(), "some-client", "Some Client");
        }

        @Override
        public KeycloakAwareSpec getKeycloakAwareSpec() {
            return (KeycloakAwareSpec) super.getCustomResourceSpec();
        }
    }
}
