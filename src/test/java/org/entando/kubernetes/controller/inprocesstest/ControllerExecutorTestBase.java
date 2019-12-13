package org.entando.kubernetes.controller.inprocesstest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorE2ETestConfig;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.junit.jupiter.api.Test;

public abstract class ControllerExecutorTestBase implements InProcessTestUtil, FluentTraversals {

    public static final String CONTROLLER_NAMESPACE = EntandoOperatorE2ETestConfig.calculateNameSpace("controller-namespace");
    protected EntandoKeycloakServer resource;
    private SimpleK8SClient<?> client;

    @Test
    public void testIt() {

        this.client = getClient();
        ControllerExecutor controllerExecutor = new ControllerExecutor(CONTROLLER_NAMESPACE, client);
        resource = newEntandoKeycloakServer();
        emulatePodWaitingBehaviour();
        controllerExecutor.startControllerFor(Action.ADDED, resource, "6.0.0");

        Pod pod = this.client.pods()
                .waitForPod(CONTROLLER_NAMESPACE, "EntandoKeycloakServer", resource.getMetadata().getName());
        assertThat(pod, is(notNullValue()));
        assertThat(theVariableNamed("ENTANDO_K8S_OPERATOR_REGISTRY").on(thePrimaryContainerOn(pod)),
                is(EntandoOperatorConfig.getEntandoDockerRegistry()));
        //TODO check other variables
        //TODO check mounts for certs, etc
    }

    protected abstract void emulatePodWaitingBehaviour();

    protected abstract SimpleK8SClient<?> getClient();
}
