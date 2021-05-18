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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.impl.AbstractK8SIntegrationTest;
import org.entando.kubernetes.controller.support.client.impl.EntandoExecListener;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultKubernetesClientForControllersTest extends AbstractK8SIntegrationTest {

    private DefaultKubernetesClientForControllers clientForControllers;
    private EntandoApp entandoApp = newTestEntandoApp();

    DefaultKubernetesClientForControllers getKubernetesClientForControllers() {
        if (clientForControllers == null) {
            prepareCrdNameMap();
            clientForControllers = new DefaultKubernetesClientForControllers(new DefaultKubernetesClient());
            clientForControllers.prepareConfig();
        }
        return clientForControllers;
    }

    @Test
    void shouldTrackDeploymentFailedStatus() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status to DeploymentFailed
        getKubernetesClientForControllers().updateStatus(entandoApp, new ExposedServerStatus("my-webapp"));
        getKubernetesClientForControllers().deploymentFailed(entandoApp, new IllegalStateException("nope"));
        final EntandoApp actual = getKubernetesClientForControllers()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        //The failure reflects on the custom resource
        assertThat(actual.getStatus().getPhase(), CoreMatchers.is(EntandoDeploymentPhase.FAILED));
        assertThat(actual.getStatus().findCurrentServerStatus().get().getEntandoControllerFailure().getFailedObjectName(),
                CoreMatchers.is(entandoApp.getMetadata().getNamespace() + "/" + entandoApp.getMetadata().getName()));

    }

    @Test
    void shouldUpdateStatusOfKnownCustomResource() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status
        getKubernetesClientForControllers().updateStatus(entandoApp, new ExposedServerStatus("my-webapp"));
        final EntandoApp actual = getKubernetesClientForControllers()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        //The updated status reflects on the custom resource
        assertTrue(actual.getStatus().forServerQualifiedBy("my-webapp").isPresent());

    }

    @Test
    void shouldUpdatePhaseOfKnownCustomResource() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status
        getKubernetesClientForControllers().updatePhase(entandoApp, EntandoDeploymentPhase.SUCCESSFUL);
        final EntandoApp actual = getKubernetesClientForControllers()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        assertThat(actual.getStatus().getPhase(), CoreMatchers.is(EntandoDeploymentPhase.SUCCESSFUL));

    }

    @Test
    void shouldUpdateStatusOfOpaqueCustomResource() throws IOException {
        prepareCrdNameMap();
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getKubernetesClientForControllers().createOrPatchEntandoResource(newTestEntandoApp());
        ObjectMapper mapper = new ObjectMapper();
        //But it is represented in an opaque format
        SerializedEntandoResource serializedEntandoResource = mapper
                .readValue(mapper.writeValueAsBytes(entandoApp), SerializedEntandoResource.class);
        serializedEntandoResource.setDefinition(
                CustomResourceDefinitionContext.fromCrd(
                        getFabric8Client().apiextensions().v1beta1().customResourceDefinitions().withName(entandoApp.getDefinitionName())
                                .get()));
        //When I update its status
        getKubernetesClientForControllers().updateStatus(serializedEntandoResource, new ExposedServerStatus("my-webapp"));
        //The updated status reflects on the custom resource
        final SerializedEntandoResource actual = getKubernetesClientForControllers().reload(serializedEntandoResource);
        assertTrue(actual.getStatus().forServerQualifiedBy("my-webapp").isPresent());

    }

    @Test
    @Disabled("Currently used for optimization only")
    void testProbes() throws InterruptedException {
        //Given I have started a new Pod
        final Pod startedPod = this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace())
                .createOrReplace(new PodBuilder()
                        .withNewMetadata()
                        .withName("my-pod")
                        .withNamespace(entandoApp.getMetadata().getNamespace())
                        .addToLabels("pod-label", "123")
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withImage("centos/nginx-116-centos7")
                        .withName("nginx")
                        .withCommand("/usr/libexec/s2i/run")
                        //                .withNewStartupProbe()
                        //                .withNewExec()
                        //                .withCommand("cat", "/tmp/started")
                        //                .endExec()
                        //                .withPeriodSeconds(5)
                        //                .withFailureThreshold(10)
                        //                .endStartupProbe()
                        .withNewLivenessProbe()
                        .withNewExec()
                        .withCommand("cat", "/tmp/live")
                        .endExec()
                        .withInitialDelaySeconds(120)
                        .withPeriodSeconds(5)
                        .withFailureThreshold(1)
                        .withSuccessThreshold(1)
                        .endLivenessProbe()
                        .withNewReadinessProbe()
                        .withNewExec()
                        .withCommand("cat", "/tmp/ready")
                        .endExec()
                        .withPeriodSeconds(5)
                        .endReadinessProbe()
                        .endContainer()
                        .endSpec()
                        .build());
        //        getKubernetesClientForControllers().executeOnPod(startedPod, "nginx", 5, "touch /tmp/started");
        getKubernetesClientForControllers().executeOnPod(startedPod, "nginx", 5, "touch /tmp/ready");
        getKubernetesClientForControllers().executeOnPod(startedPod, "nginx", 5, "touch /tmp/live");
        getKubernetesClientForControllers().executeOnPod(startedPod, "nginx", 5, "rm /tmp/live");
        assertThat(startedPod, is(notNullValue()));
    }

    @Test
    void shouldExecuteCommandOnPodAndWait() throws IOException, InterruptedException {
        //There is no way to emulate this "exec" fails on the Mock server
        //Given I have started a new Pod
        this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace()).withName("my-pod").delete();
        this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace()).withName("my-pod")
                .waitUntilCondition(Objects::isNull, 10L, TimeUnit.SECONDS);
        final Pod startedPod = this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace())
                .create(new PodBuilder()
                        .withNewMetadata()
                        .withName("my-pod")
                        .withNamespace(entandoApp.getMetadata().getNamespace())
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
        if (EntandoOperatorTestConfig.emulateKubernetes()) {
            scheduler.schedule(() -> {
                this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace()).withName(startedPod.getMetadata().getName())
                        .patch(podWithReadyStatus(startedPod));
            }, 1000L, TimeUnit.MILLISECONDS);
        }
        //When I wait for the pod
        final Pod pod = this.fabric8Client.pods().inNamespace(entandoApp.getMetadata().getNamespace())
                .withName(startedPod.getMetadata().getName())
                .waitUntilCondition(pod1 -> pod1 != null && pod1.getStatus() != null && PodResult.of(pod1).getState() == State.READY, 30L,
                        TimeUnit.SECONDS);
        final EntandoExecListener execWatch = getKubernetesClientForControllers().executeOnPod(pod, "nginx", 10, "echo 'hello world'");
        //Then the current thread only proceeds once the pod is ready
        final List<String> lines = execWatch.getOutput();
        assertThat(lines, hasItem("hello world"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{entandoApp.getMetadata().getNamespace()};
    }
}
