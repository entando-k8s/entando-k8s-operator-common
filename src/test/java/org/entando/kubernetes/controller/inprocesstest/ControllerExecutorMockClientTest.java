package org.entando.kubernetes.controller.inprocesstest;

import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.junit.jupiter.api.Tag;

@Tag("in-process")
public class ControllerExecutorMockClientTest extends ControllerExecutorTestBase {

    @Override
    protected void emulatePodWaitingBehaviour() {

    }

    @Override
    protected SimpleK8SClient<?> getClient() {
        return new SimpleK8SClientDouble();
    }
}
