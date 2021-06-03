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

package org.entando.kubernetes.controller.spi.client.impl;

import static io.qameta.allure.Allure.step;
import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.apps.v1.DeploymentOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.PersistentVolumeClaimOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.SecretOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.ServiceOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.extensions.v1beta1.IngressOperationsImpl;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.client.ExecutionResult;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.impl.AbstractK8SIntegrationTest;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@Feature("As a controller developer, I would like perform common operations on the relevant Kubernetes resources through a simple "
        + "interface to reduce the learning curve")
@EnableRuleMigrationSupport
class DefaultKubernetesClientForControllersTest extends AbstractK8SIntegrationTest {

    private DefaultKubernetesClientForControllers clientForControllers;
    private TestResource testResource;

    DefaultKubernetesClientForControllers getKubernetesClientForControllers() throws IOException {
        if (clientForControllers == null) {
            prepareCrdNameMap();
            clientForControllers = new DefaultKubernetesClientForControllers(getFabric8Client());
            clientForControllers.prepareConfig();
        }
        return clientForControllers;
    }

    @BeforeEach
    void deleteAll() throws IOException {
        super.prepareCrdNameMap();
        deleteAll(getFabric8Client().apps().deployments());
        deleteAll(getFabric8Client().pods());
        deleteAll(getFabric8Client().customResources(TestResource.class));
        deleteAll(getFabric8Client().v1().events());
    }

    @Test
    @Description("Track deployment failure on the status of the custom resource status and in Kubernetes events")
    void shouldTrackDeploymentFailedStatus() {
        step("Given I have created an instance of the CustomResourceDefinition TestResource", () -> {
            this.testResource = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestResource());
            attachResource("TestResource", this.testResource);
        });
        step("When I update its status to DeploymentFailed", () -> {
            getKubernetesClientForControllers().updateStatus(testResource, new ExposedServerStatus("my-webapp"));
            getKubernetesClientForControllers().deploymentFailed(testResource, new IllegalStateException("nope"), null);
        });
        step("Then the failure reflects on the TestResource", () -> {
            final TestResource actual = getKubernetesClientForControllers()
                    .load(TestResource.class, testResource.getMetadata().getNamespace(), testResource.getMetadata().getName());
            assertThat(actual.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
            assertThat(actual.getStatus().findCurrentServerStatus().get().getEntandoControllerFailure().getFailedObjectName())
                    .isEqualTo(testResource.getMetadata().getNamespace() + "/" + testResource.getMetadata().getName());
            attachResource("TestResource", actual);
        });
        step("And the failure event has been issued to Kubernetes", () -> {
            final List<Event> events = getKubernetesClientForControllers().listEventsFor(testResource);
            attachResources("Events", events);
            assertThat(events).allMatch(event -> event.getInvolvedObject().getName().equals(testResource.getMetadata().getName()));
            assertThat(events).anyMatch(event -> event.getAction().equals("FAILED"));
        });

    }

    @Test
    @Description("Track status updates of individual components on the status of the custom resource status and in Kubernetes events")
    void shouldUpdateStatusOfKnownCustomResource() {
        step("Given I have created an instance of the CustomResourceDefinition TestResource", () -> {
            this.testResource = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestResource());
            attachResource("TestResource", this.testResource);
        });
        step("When I update its status with an ExposedServiceStatus", () ->
                getKubernetesClientForControllers().updateStatus(testResource, new ExposedServerStatus("my-webapp")));
        step("Then the updated ExposedServiceStatus reflects on the TestResource", () -> {
            final TestResource actual = getKubernetesClientForControllers()
                    .load(TestResource.class, testResource.getMetadata().getNamespace(), testResource.getMetadata().getName());
            attachResource("TestResource", actual);
            assertThat(actual.getStatus().getServerStatus("my-webapp")).isPresent();
        });
        step("And a STATUS_CHANGE event has been issued to Kubernetes", () -> {
            final List<Event> events = getKubernetesClientForControllers().listEventsFor(testResource);
            attachResources("Events", events);
            assertThat(events).allMatch(event -> event.getInvolvedObject().getName().equals(testResource.getMetadata().getName()));
            assertThat(events).anyMatch(event -> event.getAction().equals("STATUS_CHANGE"));
        });

    }

    @Test
    @Description("Should track phase updates on the status of the custom resource and in Kubernetes events")
    void shouldUpdatePhaseOfKnownCustomResource() {
        step("Given I have created an instance of the CustomResourceDefinition TestResource", () -> {
            this.testResource = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestResource());
            attachResource("TestResource", testResource);
        });
        step("When I update its phase to 'SUCCESSFUL'", () ->
                getKubernetesClientForControllers().updatePhase(testResource, EntandoDeploymentPhase.SUCCESSFUL));
        step("Then the phase on the status of the TestResource is 'SUCCESSFUL' ", () -> {
            final TestResource actual = getKubernetesClientForControllers()
                    .load(TestResource.class, testResource.getMetadata().getNamespace(), testResource.getMetadata().getName());
            assertThat(actual.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.SUCCESSFUL);
            attachResource("TestResource", actual);
        });
        step("And a PHASE_CHANGE event has been issued to Kubernetes", () -> {
            final List<Event> events = getKubernetesClientForControllers().listEventsFor(testResource);
            attachResources("Events", events);
            assertThat(events).allMatch(event -> event.getInvolvedObject().getName().equals(testResource.getMetadata().getName()));
            assertThat(events).anyMatch(event -> event.getAction().equals("PHASE_CHANGE"));
        });
    }

    @Test
    @Description("Should track phase updates on the status of opaque custom resources and in Kubernetes events")
    void shouldUpdateStatusOfOpaqueCustomResource() throws IOException {
        step("Given I have created an instance of the CustomResourceDefinition TestResource", () -> {
            this.testResource = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestResource());
            attachResource("TestResource", testResource);
        });
        SerializedEntandoResource serializedEntandoResource = objectMapper
                .readValue(objectMapper.writeValueAsBytes(testResource), SerializedEntandoResource.class);
        step("But it is represented in an opaque format using the SerializedEntandoResource class", () -> {
            serializedEntandoResource.setDefinition(
                    CustomResourceDefinitionContext.fromCustomResourceType(TestResource.class));
            attachResource("Opaque Resource", serializedEntandoResource);
        });
        step("When I update its status with an ExposedServerStatus", () ->
                getKubernetesClientForControllers().updateStatus(serializedEntandoResource, new ExposedServerStatus("my-webapp")));
        step("Then the updated status reflects on the SerializedEntandoResource", () -> {
            final SerializedEntandoResource actual = getKubernetesClientForControllers().reload(serializedEntandoResource);
            assertThat(actual.getStatus().getServerStatus("my-webapp")).isPresent();
            attachResource("TestResource", actual);
        });
        step("And a STATUS_CHANGE event has been issued to Kubernetes", () -> {
            final List<Event> events = getKubernetesClientForControllers().listEventsFor(testResource);
            attachResources("Events", events);
            assertThat(events).allMatch(event -> event.getInvolvedObject().getName().equals(testResource.getMetadata().getName()));
            assertThat(events).anyMatch(event -> event.getAction().equals("STATUS_CHANGE"));
        });

    }

    @Test
    @Description("Should retrieve custom resource generically without the need of an implementation class")
    void shouldRetrieveCustomResourcesGenerically() {
        final int numberOfReplicas = 5;
        step("Given I have created an instance of the CustomResourceDefinition TestResource", () -> {
            this.testResource = getKubernetesClientForControllers()
                    .createOrPatchEntandoResource(newTestResource().withSpec(new BasicDeploymentSpecBuilder().withReplicas(
                            numberOfReplicas).build()));
            attachResource("TestResource", testResource);
        });
        ValueHolder<SerializedEntandoResource> serializedEntandoResource = new ValueHolder<>();
        step("When I retrieve the TestResource without using the implementation class", () -> {
            serializedEntandoResource.set(
                    getKubernetesClientForControllers()
                            .loadCustomResource(this.testResource.getApiVersion(), this.testResource.getKind(),
                                    this.testResource.getMetadata().getNamespace(), this.testResource.getMetadata().getName()));
            attachResource("Opaque resource", serializedEntandoResource.get());
        });
        step("Then it reflects the same state as the original resource", () ->
                assertThat(serializedEntandoResource.get().getSpec()).containsEntry("replicas", numberOfReplicas));
    }

    @Test
    @Description("Should retrieve standard Kubernetes resources generically without needing to know the implementation class")
    void shouldRetrieveStandardResourceGenerically() {
        final int numberOfReplicas = 5;
        step("Given I have created an instance of a Pod", () -> {
            final Pod startedPod = this.fabric8Client.pods().inNamespace(newTestResource().getMetadata().getNamespace())
                    .create(new PodBuilder()
                            .withNewMetadata()
                            .withName("my-pod")
                            .withNamespace(newTestResource().getMetadata().getNamespace())
                            .addToLabels("pod-label", "123")
                            .endMetadata()
                            .withNewSpec()
                            .addNewContainer()
                            .withImage("centos/nginx-116-centos7")
                            .withName("nginx")
                            .withCommand("/usr/libexec/s2i/run")
                            .endContainer()
                            .endSpec()
                            .build());
            attachResource("Pod", startedPod);
        });
        ValueHolder<Pod> pod = new ValueHolder<>();
        step("When I retrieve the Pod without using the implementation class", () -> {
            pod.set((Pod) getKubernetesClientForControllers()
                    .loadStandardResource("Pod", this.newTestResource().getMetadata().getNamespace(), "my-pod"));
            attachResource("Opaque resource", pod.get());
        });
        step("Then it is found", () -> assertThat(pod.get()).isNotNull());
        step("And the same applies for Deployments, Services, Ingresses, Secrets and PersistentVolumeClaims", () -> {
            assertThat(SupportedStandardResourceKind.DEPLOYMENT.getOperation(getFabric8Client()))
                    .isInstanceOf(DeploymentOperationsImpl.class);
            assertThat(SupportedStandardResourceKind.SERVICE.getOperation(getFabric8Client())).isInstanceOf(ServiceOperationsImpl.class);
            assertThat(SupportedStandardResourceKind.SECRET.getOperation(getFabric8Client())).isInstanceOf(SecretOperationsImpl.class);
            assertThat(SupportedStandardResourceKind.INGRESS.getOperation(getFabric8Client())).isInstanceOf(IngressOperationsImpl.class);
            assertThat(SupportedStandardResourceKind.PERSISTENT_VOLUME_CLAIM.getOperation(getFabric8Client())).isInstanceOf(
                    PersistentVolumeClaimOperationsImpl.class);
        });
    }

    @Test
    @Description("Should execute commands against pods and reflect the correct result code")
    void shouldExecuteCommandOnPodAndWait() throws IOException, InterruptedException, TimeoutException {
        step("Given I have started a new Pod", () -> {
            this.fabric8Client.pods().inNamespace(newTestResource().getMetadata().getNamespace()).withName("my-pod")
                    .waitUntilCondition(Objects::isNull, 20L, TimeUnit.SECONDS);
            final Pod startedPod = this.fabric8Client.pods().inNamespace(newTestResource().getMetadata().getNamespace())
                    .create(new PodBuilder()
                            .withNewMetadata()
                            .withName("my-pod")
                            .withNamespace(newTestResource().getMetadata().getNamespace())
                            .addToLabels("pod-label", "123")
                            .endMetadata()
                            .withNewSpec()
                            .addNewContainer()
                            .withImage("centos/nginx-116-centos7")
                            .withName("nginx")
                            .withCommand("/usr/libexec/s2i/run")
                            .endContainer()
                            .endSpec()
                            .build());
            attachResource("Pod", startedPod);
        });
        ValueHolder<Pod> pod = new ValueHolder<>();
        step("And I wait for the pod to be ready", () -> {
            pod.set(this.fabric8Client.pods().inNamespace(newTestResource().getMetadata().getNamespace())
                    .withName("my-pod")
                    .waitUntilCondition(pod1 -> pod1 != null && pod1.getStatus() != null && PodResult.of(pod1).getState() == State.READY,
                            30L,
                            TimeUnit.SECONDS));
        });
        ValueHolder<ExecutionResult> success = new ValueHolder<>();
        ValueHolder<ExecutionResult> failure = new ValueHolder<>();
        step("When I execute a valid command and in invalid command", () -> {
            success.set(getKubernetesClientForControllers().executeOnPod(pod.get(), "nginx", 10, "echo 'hello world'"));
            failure.set(getKubernetesClientForControllers()
                    .executeOnPod(pod.get(), "nginx", 10, "asdfasdfasf", "echo 'hello world'"));
        });
        step("Then the the return code of the valid command is 0", () -> {
            assertThat(success.get().getOutputLines()).contains("hello world");
            assertThat(success.get().getCode()).isZero();
        });
        step("Then the the return code of the invalid command is non-zero", () -> {
            assertThat(failure.get().getOutputLines()).contains("sh: asdfasdfasf: command not found");
            assertThat(failure.get().getOutputLines()).doesNotContain("hello world");
            assertThat(failure.get().getCode()).isNotZero();
        });
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{newTestResource().getMetadata().getNamespace()};
    }
}