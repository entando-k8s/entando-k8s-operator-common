package org.entando.kubernetes.controller.test.mocked

import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient
import org.entando.kubernetes.controller.test.BareBonesDeployableSpecBase

class BareBonesDeployableMockedTest extends BareBonesDeployableSpecBase {
    private SimpleK8SClientDouble client

    @Override
    SimpleK8SClient<?> getClient() {
        if (client == null) {
            client = new SimpleK8SClientDouble()
        }
        return client;
    }
}
