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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.controller.test.support.PodBehavior;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S5786")
public abstract class ControllerExecutorTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior {

    public static final String CONTROLLER_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("controller-namespace");
    protected EntandoKeycloakServer resource;
    private SimpleK8SClient<?> client;

    @AfterEach
    void resetSystemProperty() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty());
        client.pods().getPodWatcherHolder().set(null);
        PodClientDouble.setEmulatePodWatching(false);
    }

    @Test
    void testStart() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty(), "9000000");
        this.client = getClient();
        ControllerExecutor controllerExecutor = new ControllerExecutor(CONTROLLER_NAMESPACE, client);
        resource = newEntandoKeycloakServer();
        emulatePodWaitingBehaviour(false);
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
        emulatePodWaitingBehaviour(false);
        Pod pod = controllerExecutor.runControllerFor(Action.ADDED, resource, "6.0.0");
        assertThat(pod, is(notNullValue()));
        assertThat(
                theVariableNamed(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.name())
                        .on(thePrimaryContainerOn(pod)),
                is("9000000"));
        //TODO check other variables
        //TODO check mounts for certs, etc
    }

    protected void emulatePodWaitingBehaviour(boolean requiresDelete) {
        PodClientDouble.setEmulatePodWatching(true);
        new Thread(() -> {
            if (requiresDelete) {
                //The delete watcher won't trigger events because the condition is true from the beginning
                await().atMost(30, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
                        .until(() -> getClient().pods().getPodWatcherHolder()
                                .getAndSet(null) != null);
            }
            //The second watcher will trigger events
            await().atMost(30, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS).until(() ->
                    this.client.pods().loadPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName()) != null);
            Pod pod = this.client.pods().loadPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName());
            getClient().pods().getPodWatcherHolder().getAndSet(null).eventReceived(Action.MODIFIED, podWithSucceededStatus(pod));
        }).start();
    }

}
