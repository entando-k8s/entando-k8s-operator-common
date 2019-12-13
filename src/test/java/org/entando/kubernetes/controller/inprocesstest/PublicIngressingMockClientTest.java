package org.entando.kubernetes.controller.inprocesstest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import io.fabric8.kubernetes.api.model.Pod;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;
import org.junit.jupiter.api.Tag;

@Tag("in-process")
public class PublicIngressingMockClientTest extends PublicIngressingTestBase {

    @Override
    protected SimpleK8SClient getClient() {
        return new SimpleK8SClientDouble();
    }

    @Override
    protected void emulatePodWaitingBehaviour() {
        lenient().when(super.k8sClient.pods().waitForPod(anyString(), anyString(), anyString())).thenReturn(podWithReadyStatus());
        lenient().when(super.k8sClient.pods().runToCompletion(any(EntandoBaseCustomResource.class), any(Pod.class)))
                .thenReturn(podWithReadyStatus());
    }

}
