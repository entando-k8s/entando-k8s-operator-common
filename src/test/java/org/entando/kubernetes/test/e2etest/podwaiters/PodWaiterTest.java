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

package org.entando.kubernetes.test.e2etest.podwaiters;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tags({@Tag("inter-process"), @Tag("pre-deployment")})
@ExtendWith(MockitoExtension.class)
class PodWaiterTest {

    private static final String PENDING_PHASE = "Pending";
    private static final String POD_SCHEDULED = "PodScheduled";
    private static final String RUNNING_PHASE = "Running";
    private static final String READY_CONDITION = "Ready";
    private static final String CONTAINERS_READY_CONDITION = "ContainersReady";
    @Mock
    private PodResource<Pod, DoneablePod> podOperationMock;

    @Test
    void initialStateNull() throws Exception {
        ServicePodWaiter mutex = new ServicePodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(PENDING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitContainerCreationTo(Duration.ofMillis(300)).limitReadinessTo(Duration.ofMillis(300))
                .waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.CREATING);
        assertFalse(mutex.timedOut);
        await().atMost(315, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.timedOut);
    }

    @Test
    void ready() throws Exception {
        ServicePodWaiter mutex = new ServicePodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitContainerCreationTo(Duration.ofMillis(300)).limitReadinessTo(Duration.ofMillis(300))
                .waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, READY_CONDITION, CONTAINERS_READY_CONDITION));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.READY);
        assertFalse(mutex.timedOut);
    }

    @Test()
    void failedRunning() throws Exception {
        ServicePodWaiter mutex = new ServicePodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitContainerCreationTo(Duration.ofMillis(1000)).limitReadinessTo(Duration.ofMillis(1000))
                .waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.ERROR, podFailingWithCondition("Failed", "Error", POD_SCHEDULED));
        await().atMost(15, TimeUnit.MILLISECONDS).pollInterval(1, TimeUnit.MILLISECONDS)
                .until(() -> mutex.state == State.COMPLETED && "Error".equals(mutex.failReason));
        assertFalse(mutex.timedOut);
        assertFalse(mutex.wasSuccessful());
    }

    @Test
    void happyFlowToCompletion() throws Exception {
        JobPodWaiter mutex = new JobPodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(PENDING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitContainerCreationTo(Duration.ofMillis(3000)).limitCompletionTo(Duration.ofMillis(3000))
                .waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.CREATING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition("Succeeded", READY_CONDITION, CONTAINERS_READY_CONDITION));
        //TODO analyse why this one fails every now and then
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.COMPLETED);
        assertFalse(mutex.timedOut);
        assertTrue(mutex.wasSuccessful());
    }

    @Test
    void someContainersNotRun() {
        PodResult podResult = PodResult.of(podWithContainerStatusMismatch());
        assertTrue(podResult.hasFailed());
        assertThat(podResult.getFailReason(), is("SomeContainersNotRun"));
    }

    private Pod podWithContainerStatusMismatch() {
        Pod pod = podWithCondition(PENDING_PHASE, POD_SCHEDULED);
        pod.getSpec().getContainers().add(new Container());
        return pod;
    }

    @Test
    void completionTimeout() throws Exception {
        JobPodWaiter mutex = new JobPodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(PENDING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitContainerCreationTo(Duration.ofMillis(300)).limitCompletionTo(Duration.ofMillis(300))
                .waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.CREATING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        await().atMost(600, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.timedOut);
        assertTrue(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition("Succeeded", READY_CONDITION, CONTAINERS_READY_CONDITION));
        assertTrue(mutex.timedOut);
        assertFalse(mutex.wasSuccessful());
    }

    @Test
    void happyFlowToReady() throws Exception {
        ServicePodWaiter mutex = new ServicePodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(PENDING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitReadinessTo(Duration.ofMillis(3000)).waitOn(podOperationMock));
        await().atMost(120, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.CREATING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        await().atMost(120, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, READY_CONDITION, CONTAINERS_READY_CONDITION));
        await().atMost(120, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.READY);
        assertFalse(mutex.timedOut);
        assertTrue(mutex.wasSuccessful());
    }

    @Test
    void readyTimeout() throws Exception {
        ServicePodWaiter mutex = new ServicePodWaiter();
        when(podOperationMock.get()).thenReturn(podWithCondition(PENDING_PHASE, POD_SCHEDULED));
        asyncUntilNewThreadWaits(() -> mutex.limitReadinessTo(Duration.ofMillis(300)).waitOn(podOperationMock));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.CREATING);
        assertFalse(mutex.timedOut);
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, POD_SCHEDULED));
        await().atMost(50, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.state == State.RUNNING);
        assertFalse(mutex.timedOut);
        await().atMost(400, TimeUnit.MILLISECONDS).pollDelay(1, TimeUnit.MILLISECONDS).until(() -> mutex.timedOut);
        //Ready happens too late
        mutex.eventReceived(Watcher.Action.MODIFIED, podWithCondition(RUNNING_PHASE, READY_CONDITION, CONTAINERS_READY_CONDITION));
        assertTrue(mutex.timedOut);
        assertFalse(mutex.wasSuccessful());
    }

    private void asyncUntilNewThreadWaits(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> thread.getState() == Thread.State.TIMED_WAITING);
    }

    private Pod podWithCondition(String phase, String... conditionTypes) {
        return podFailingWithCondition(phase, null, conditionTypes);
    }

    private Pod podFailingWithCondition(String phase, String reason, String... conditionType) {
        return new PodBuilder()
                .withNewMetadata().withName("foefie-faffie").endMetadata()
                .withNewSpec()
                .addNewContainer()
                .endContainer()
                .addNewInitContainer()
                .endInitContainer()
                .endSpec()
                .withNewStatus()
                .addNewContainerStatus()
                .withNewState().endState()
                .endContainerStatus()
                .addNewInitContainerStatus()
                .withNewState().endState()
                .endInitContainerStatus()
                .withPhase(phase)
                .withReason(reason)
                .withConditions(buildTrueConditions(conditionType))
                .endStatus().build();
    }

    private List<PodCondition> buildTrueConditions(String... conditionTypes) {
        return Arrays.stream(conditionTypes).map(s -> new PodConditionBuilder().withType(s).withStatus("True").build())
                .collect(Collectors.toList());
    }

    //

    //
    //    @Test
    //    void exceptionOnPulling() throws Exception {
    //        ServicePodWaiter mutex = new ServicePodWaiter();
    //        when(podOperationMock.get()).thenReturn(podWithContainerStatus(
    //                builder -> builder.withNewState().withNewWaiting().withReason(CONTAINER_CREATING).endWaiting()
    //                        .endState()));
    //        asyncUntilNewThreadWaits(() -> {
    //            try {
    //                sleep(250);
    //            } catch (InterruptedException e) {
    //                // ignored
    //            }
    //            mutex.eventReceived(Watcher.Action.ERROR, podWithContainerStatus(
    //                    builder -> builder.withReady(true).withNewState().withNewWaiting().withMessage(IMAGE_PULL_BACK_OFF)
    //                            .endWaiting().endState()));
    //        });
    //        assertThrows(IllegalStateException.class,
    //                () -> mutex.throwException(IllegalStateException.class).waitOn(podOperationMock));
    //        assertFalse(mutex.wasSuccessful());
    //    }
    //
    //
}
