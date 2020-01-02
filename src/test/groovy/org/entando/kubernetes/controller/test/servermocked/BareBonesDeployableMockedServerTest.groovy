package org.entando.kubernetes.controller.test.servermocked

import io.fabric8.kubernetes.client.server.mock.KubernetesServer
import org.entando.kubernetes.client.DefaultSimpleK8SClient
import org.entando.kubernetes.controller.integrationtest.support.FluentIntegrationTesting
import org.entando.kubernetes.controller.integrationtest.support.TestFixturePreparation
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient
import org.entando.kubernetes.controller.test.BareBonesDeployableSpecBase
import org.entando.kubernetes.model.app.EntandoApp
import org.junit.Before
import org.junit.Rule

class BareBonesDeployableMockedServerTest extends BareBonesDeployableSpecBase implements FluentIntegrationTesting {
    @Rule
    KubernetesServer mockServer = new KubernetesServer(false, true)
    private DefaultSimpleK8SClient client

    @Before
    def beforeTest() {
        TestFixturePreparation.prepareTestFixture(mockServer.getClient(), deleteAll(EntandoApp).fromNamespace(TEST_NAMESPACE))
    }

    @Override
    SimpleK8SClient<?> getClient() {
        if (client == null) {
            client = new DefaultSimpleK8SClient(mockServer.getClient())
        }
        return client
    }
}
