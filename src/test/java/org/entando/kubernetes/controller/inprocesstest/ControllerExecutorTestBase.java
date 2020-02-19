package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.client.PodWatcher;
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

public abstract class ControllerExecutorTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior {

    public static final String CONTROLLER_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("controller-namespace");
    protected EntandoKeycloakServer resource;
    private SimpleK8SClient<?> client;

    @AfterEach
    public void resetSystemProperty() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS.getJvmSystemProperty());
    }

    @Test
    public void testStart() {
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
    public void testRun() {
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
        PodClientDouble.setEmulatePodWatching(true);
        new Thread(() -> {
            AtomicReference<PodWatcher> podWatcherHolder = getClient().pods().getPodWatcherHolder();
            await().atMost(30, TimeUnit.SECONDS).until(() -> podWatcherHolder.get() != null);
            Pod pod = this.client.pods().loadPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName());
            podWatcherHolder.getAndSet(null).eventReceived(Action.MODIFIED, podWithSucceededStatus(pod));
        }).start();
    }

}
