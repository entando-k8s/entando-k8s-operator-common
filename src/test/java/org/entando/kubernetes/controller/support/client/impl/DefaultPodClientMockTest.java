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

package org.entando.kubernetes.controller.support.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DefaultPodClientMockTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private MixedOperation<Pod, PodList, PodResource> podsOperation;

    @Mock
    private MixedOperation<Pod, PodList, PodResource> namespacedPodsOperation;

    @Mock
    private FilterWatchListDeletable<Pod, PodList, PodResource> labeledPodsOperation;

    @Mock
    private PodResource podResource;

    @Mock
    private PodList podList;

    private DefaultPodClient defaultPodClient;

    @BeforeEach
    void setUp() {
        defaultPodClient = new DefaultPodClient(kubernetesClient);
        lenient().when(kubernetesClient.pods()).thenReturn(podsOperation);
    }

    @Test
    void shouldRemoveSuccessfullyCompletedPods() {
        // Given
        String namespace = "test-namespace";
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "test");

        Pod completedPod = createPodWithPhase("completed-pod", "Succeeded", false);
        Pod failedPod = createPodWithPhase("failed-pod", "Failed", true);
        Pod runningPod = createPodWithPhase("running-pod", "Running", false);

        when(podsOperation.inNamespace(namespace)).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withLabels(labels)).thenReturn(labeledPodsOperation);
        when(labeledPodsOperation.list()).thenReturn(podList);
        when(podList.getItems()).thenReturn(Arrays.asList(completedPod, failedPod, runningPod));

        // When
        defaultPodClient.removeSuccessfullyCompletedPods(namespace, labels);

        // Then
        verify(namespacedPodsOperation).delete(completedPod);
    }

    @Test
    void shouldDeletePod() {
        // Given
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .withNamespace("test-namespace")
                .endMetadata()
                .build();

        when(podsOperation.inNamespace("test-namespace")).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withName("test-pod")).thenReturn(podResource);

        // When
        defaultPodClient.deletePod(pod);

        // Then
        verify(podResource).delete();
    }

    @Test
    void shouldStartPod() {
        // Given
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .withNamespace("test-namespace")
                .endMetadata()
                .build();

        Pod createdPod = new PodBuilder(pod)
                .withNewStatus()
                .withPhase("Running")
                .endStatus()
                .build();

        when(podsOperation.inNamespace("test-namespace")).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.create(pod)).thenReturn(createdPod);

        // When
        Pod result = defaultPodClient.start(pod);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus().getPhase()).isEqualTo("Running");
        verify(namespacedPodsOperation).create(pod);
    }

    @Test
    void shouldLoadPodByLabels() {
        // Given
        String namespace = "test-namespace";
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "test");

        Pod expectedPod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .withNamespace(namespace)
                .addToLabels(labels)
                .endMetadata()
                .build();

        when(podsOperation.inNamespace(namespace)).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withLabels(labels)).thenReturn(labeledPodsOperation);
        when(labeledPodsOperation.list()).thenReturn(podList);
        when(podList.getItems()).thenReturn(Collections.singletonList(expectedPod));

        // When
        Pod result = defaultPodClient.loadPod(namespace, labels);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getName()).isEqualTo("test-pod");
    }

    @Test
    void shouldReturnNullWhenNoPodFoundByLabels() {
        // Given
        String namespace = "test-namespace";
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "nonexistent");

        when(podsOperation.inNamespace(namespace)).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withLabels(labels)).thenReturn(labeledPodsOperation);
        when(labeledPodsOperation.list()).thenReturn(podList);
        when(podList.getItems()).thenReturn(Collections.emptyList());

        // When
        Pod result = defaultPodClient.loadPod(namespace, labels);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldRunPodToCompletion() throws TimeoutException {
        // Given
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("test-container")
                .withImage("busybox")
                .endContainer()
                .endSpec()
                .build();

        Pod completedPod = new PodBuilder(pod)
                .withNewStatus()
                .withPhase("Succeeded")
                .addNewContainerStatus()
                .withName("test-container")
                .withNewState()
                .withNewTerminated()
                .withExitCode(0)
                .endTerminated()
                .endState()
                .endContainerStatus()
                .endStatus()
                .build();

        when(podsOperation.inNamespace("test-namespace")).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.create(pod)).thenReturn(pod);
        when(namespacedPodsOperation.withName("test-pod")).thenReturn(podResource);
        when(podResource.waitUntilCondition(any(), anyLong(), any(TimeUnit.class))).thenReturn(completedPod);

        // When
        Pod result = defaultPodClient.runToCompletion(pod, 60);

        // Then
        assertThat(result).isNotNull();
        assertThat(PodResult.of(result).getState()).isEqualTo(PodResult.State.COMPLETED);
        verify(namespacedPodsOperation).create(pod);
    }

    @Test
    void shouldRemoveAndWaitForPodDeletion() throws TimeoutException {
        // Given
        String namespace = "test-namespace";
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "test");

        CompletableFuture<List<Pod>> future = CompletableFuture.completedFuture(Collections.emptyList());

        when(podsOperation.inNamespace(namespace)).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withLabels(labels)).thenReturn(labeledPodsOperation);
        when(labeledPodsOperation.informOnCondition(any())).thenReturn(future);

        // When
        defaultPodClient.removeAndWait(namespace, labels, 60);

        // Then
        verify(labeledPodsOperation).delete();
        verify(labeledPodsOperation).informOnCondition(any());
    }

    @Test
    void shouldDetectPodWithFailedContainersInWaitingState() throws TimeoutException {
        // Given a pod with a container in error waiting state
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("test-container")
                .withImage("busybox")
                .endContainer()
                .endSpec()
                .build();

        Pod podWithErrorWaiting = new PodBuilder(pod)
                .withNewStatus()
                .withPhase("Pending")
                .addNewContainerStatus()
                .withName("test-container")
                .withNewState()
                .withNewWaiting()
                .withReason("ImagePullBackOff")
                .endWaiting()
                .endState()
                .endContainerStatus()
                .endStatus()
                .build();

        // Modify to have error in waiting reason
        podWithErrorWaiting.getStatus().getContainerStatuses().get(0)
                .getState().getWaiting().setReason("CreateContainerError");

        when(podsOperation.inNamespace("test-namespace")).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.create(pod)).thenReturn(pod);
        when(namespacedPodsOperation.withName("test-pod")).thenReturn(podResource);
        when(podResource.waitUntilCondition(any(), anyLong(), any(TimeUnit.class))).thenReturn(podWithErrorWaiting);

        // When
        Pod result = defaultPodClient.runToCompletion(pod, 60);

        // Then - should return the pod with error (hasPodFailedContainers returned true)
        assertThat(result).isNotNull();
        assertThat(result.getStatus().getContainerStatuses().get(0).getState().getWaiting().getReason())
                .contains("Error");
    }

    @Test
    void shouldWaitForPodWithLabel() throws TimeoutException {
        // Given
        String namespace = "test-namespace";
        String labelName = "app";
        String labelValue = "my-app";

        Pod readyPod = createPodWithPhase("test-pod", "Running", false);

        CompletableFuture<List<Pod>> future = CompletableFuture.completedFuture(Collections.singletonList(readyPod));

        when(podsOperation.inNamespace(namespace)).thenReturn(namespacedPodsOperation);
        when(namespacedPodsOperation.withLabel(labelName, labelValue)).thenReturn(labeledPodsOperation);
        when(labeledPodsOperation.informOnCondition(any())).thenReturn(future);

        // When
        Pod result = defaultPodClient.waitForPod(namespace, labelName, labelValue, 60);

        // Then
        assertThat(result).isNotNull();
        verify(labeledPodsOperation).informOnCondition(any());
    }

    @Test
    void shouldHandleTimeoutInWaitUntilCondition() {
        // Given
        CompletableFuture<List<Pod>> neverCompletingFuture = new CompletableFuture<>();

        when(labeledPodsOperation.informOnCondition(any())).thenReturn(neverCompletingFuture);

        // When - use a very short timeout
        Pod result = DefaultPodClient.waitUntilCondition(
                labeledPodsOperation,
                pod -> pod != null,
                1,
                TimeUnit.MILLISECONDS
        );

        // Then - should return null on timeout
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenConditionMatchesNullForEmptyList() {
        // Given
        CompletableFuture<List<Pod>> emptyListFuture = CompletableFuture.completedFuture(Collections.emptyList());

        when(labeledPodsOperation.informOnCondition(any())).thenReturn(emptyListFuture);

        // When
        Pod result = DefaultPodClient.waitUntilCondition(
                labeledPodsOperation,
                pod -> pod == null, // Condition that matches null
                60,
                TimeUnit.SECONDS
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldWaitUntilConditionOnList() {
        // Given
        Pod pod1 = createPodWithPhase("pod-1", "Running", false);
        Pod pod2 = createPodWithPhase("pod-2", "Running", false);
        CompletableFuture<List<Pod>> listFuture = CompletableFuture.completedFuture(Arrays.asList(pod1, pod2));

        when(labeledPodsOperation.informOnCondition(any())).thenReturn(listFuture);

        // When
        boolean result = DefaultPodClient.waitUntilConditionOnList(
                labeledPodsOperation,
                list -> list.size() == 2,
                60,
                TimeUnit.SECONDS
        );

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseOnTimeoutInWaitUntilConditionOnList() {
        // Given
        CompletableFuture<List<Pod>> neverCompletingFuture = new CompletableFuture<>();

        when(labeledPodsOperation.informOnCondition(any())).thenReturn(neverCompletingFuture);

        // When - use a very short timeout
        boolean result = DefaultPodClient.waitUntilConditionOnList(
                labeledPodsOperation,
                list -> !list.isEmpty(),
                1,
                TimeUnit.MILLISECONDS
        );

        // Then
        assertThat(result).isFalse();
    }

    // Helper methods

    private Pod createPodWithPhase(String name, String phase, boolean failed) {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("container")
                .withImage("busybox")
                .endContainer()
                .endSpec()
                .build();

        PodStatus status = new PodStatus();
        status.setPhase(phase);

        if ("Succeeded".equals(phase)) {
            ContainerStatus containerStatus = new ContainerStatus();
            containerStatus.setName("container");
            ContainerState state = new ContainerState();
            state.setTerminated(new io.fabric8.kubernetes.api.model.ContainerStateTerminated());
            state.getTerminated().setExitCode(failed ? 1 : 0);
            containerStatus.setState(state);
            status.setContainerStatuses(Collections.singletonList(containerStatus));
        } else if ("Failed".equals(phase)) {
            ContainerStatus containerStatus = new ContainerStatus();
            containerStatus.setName("container");
            ContainerState state = new ContainerState();
            state.setTerminated(new io.fabric8.kubernetes.api.model.ContainerStateTerminated());
            state.getTerminated().setExitCode(1);
            containerStatus.setState(state);
            status.setContainerStatuses(Collections.singletonList(containerStatus));
        } else if ("Running".equals(phase)) {
            ContainerStatus containerStatus = new ContainerStatus();
            containerStatus.setName("container");
            containerStatus.setReady(true);
            ContainerState state = new ContainerState();
            state.setRunning(new io.fabric8.kubernetes.api.model.ContainerStateRunning());
            containerStatus.setState(state);
            status.setContainerStatuses(Collections.singletonList(containerStatus));
        }

        pod.setStatus(status);
        return pod;
    }
}