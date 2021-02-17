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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.PodClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.controller.ControllerExecutor;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.PodBehavior;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S5786")
public abstract class ControllerExecutorTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior {

    public static final String CONTROLLER_NAMESPACE = "controller-namespace";
    protected EntandoKeycloakServer resource;
    private SimpleK8SClient<?> client;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @BeforeEach
    public void enableQueueing() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
    }

    @AfterEach
    void resetSystemProperty() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty());
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        scheduler.shutdownNow();
        getClient().pods().getPodWatcherQueue().clear();
    }

    @Test
    void testStart() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty(), "9000000");
        this.client = getClient();
        ControllerExecutor controllerExecutor = new ControllerExecutor(CONTROLLER_NAMESPACE, client);
        resource = newEntandoKeycloakServer();
        emulatePodWaitingBehaviour();
        controllerExecutor.startControllerFor(Action.ADDED, resource, "6.0.0");

        Pod pod = this.client.pods()
                .waitForPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName());
        assertThat(pod, is(notNullValue()));
        assertThat(
                theVariableNamed(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.name())
                        .on(thePrimaryContainerOn(pod)),
                is("9000000"));
        //TODO check other variables
        //TODO check mounts for certs, etc
    }

    @Test
    void testRun() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty(), "9000000");
        this.client = getClient();
        ControllerExecutor controllerExecutor = new ControllerExecutor(CONTROLLER_NAMESPACE, client);
        resource = newEntandoKeycloakServer();
        emulatePodWaitingBehaviour();
        Pod pod = controllerExecutor.runControllerFor(Action.ADDED, resource, "6.0.0");
        assertThat(pod, is(notNullValue()));
        assertThat(
                theVariableNamed(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.name())
                        .on(thePrimaryContainerOn(pod)),
                is("9000000"));
        //TODO check other variables
        //TODO check mounts for certs, etc
    }

    protected void emulatePodWaitingBehaviour() {
        PodClientDouble.ENQUEUE_POD_WATCH_HOLDERS.set(true);
        scheduler.schedule(() -> {
            try {
                //The delete watcher won't need events to be triggered because the condition is true from the beginning
                getClient().pods().getPodWatcherQueue().take();
                //The second watcher will trigger events
                PodWatcher controllerPodWatcher = getClient().pods().getPodWatcherQueue().take();
                await().atMost(30, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS).until(() ->
                        this.client.pods().loadPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName())
                                != null);
                Pod pod = this.client.pods().loadPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName());
                controllerPodWatcher.eventReceived(Action.MODIFIED, podWithSucceededStatus(pod));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 30, TimeUnit.MILLISECONDS);
    }

}
