package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.app.DoneableEntandoApp;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppList;
import org.entando.kubernetes.model.app.EntandoAppOperationFactory;

public class EntandoAppIntegrationTestHelper extends IntegrationTestHelperBase<EntandoApp, EntandoAppList, DoneableEntandoApp> {

    public static final String TEST_NAMESPACE = EntandoOperatorE2ETestConfig.getTestNamespaceOverride().orElse("test-namespace");
    public static final String TEST_APP_NAME = "test-entando";

    public EntandoAppIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoAppOperationFactory::produceAllEntandoApps);
    }

    public void createAndWaitForApp(EntandoApp entandoApp, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(TEST_NAMESPACE).create(entandoApp);
        if (deployingDbContainers) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    TEST_NAMESPACE, TEST_APP_NAME + "-db");
        }
        this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)),
                TEST_NAMESPACE,
                TEST_APP_NAME + "-db-preparation-job");
        //300 because there are 3 containers
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(300 + waitOffset)),
                TEST_NAMESPACE, TEST_APP_NAME + "-server");
        waitFor(30).seconds().until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(TEST_NAMESPACE)
                            .withName(TEST_APP_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("server").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });

        waitFor(30).seconds().until(() -> HttpTestHelper.read(
                TlsHelper.getDefaultProtocol() + "://" + entandoApp.getSpec().getIngressHostName()
                        .orElseThrow(() -> new IllegalStateException())
                        + "/entando-de-app/index.jsp").contains("Entando - Welcome"));
    }

}
