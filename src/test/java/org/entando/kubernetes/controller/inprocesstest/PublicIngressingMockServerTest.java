package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.PodResult.RUNNING_PHASE;
import static org.entando.kubernetes.controller.PodResult.SUCCEEDED_PHASE;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.client.DefaultPodClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.client.PodWatcherHolder;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.junit.Rule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

/**
 * This is the new approach for testing this module. It uses the Kubernetes mock server. At this point in time, it seems to be a 50/50
 * decision whether to use the mock server or to mock the SimpleClient interfaces. Pros and cons are:
 *<br/>
 * Pros.
 *<br/>
 * We can test most of the Client classes now.
 *<br/>
 * We can trap issues with invalid or missing fields earlier.
 *<br/>
 * Cons:
 *<br/>
 * The resulting tests are about 10 time slower than Mockito level mocking.
 *<br/>
 * The mock server doesn't support Watches, so it was quite difficult to emulate the Websocket logic.
 *<br/>
 * We still don't cover the Keycloak client.
 *<br/>
 * The mockServer doesn't automatically generate statuses, so we still have to set them somehow to test status updates.
 *<br/>
 * Future possibilities:
 *<br/>
 * We can perhaps implement test cases to run in one of three modes:
 *<br/>
 * 1. Mockito mocked.
 *<br/>
 * 2. Mockserver.
 *<br/>
 * 3. Actual server.
 */
@Tag("in-process")
@EnableRuleMigrationSupport
public class PublicIngressingMockServerTest extends PublicIngressingTestBase {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private AtomicReference<PodWatcher> currentPodWatcher = new AtomicReference<>();
    PodWatcherHolder podWatcherHolder = new PodWatcherHolder() {
        @Override
        public void current(PodWatcher w) {
            currentPodWatcher.set(w);
        }
    };

    @Override
    protected SimpleK8SClient getClient() {
        return new DefaultSimpleK8SClient(server.getClient());
    }

    @Override
    protected void emulatePodWaitingBehaviour() {
        new Thread(() -> {
            DefaultPodClient.setPodWatcherHolder(podWatcherHolder);
            await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
            currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithReadyStatus());
            await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
            currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithSucceededStatus());
            await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
            currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithReadyStatus());
        }).start();
    }

    protected Pod podWithReadyStatus() {
        return podWithStatus(new PodStatusBuilder().withPhase(RUNNING_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build());
    }

    protected Pod podWithSucceededStatus() {
        return podWithStatus(new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build());
    }

    private Pod podWithStatus(PodStatus status) {
        return k8sClient.pods().start(new PodBuilder().withNewMetadata()
                .withName(SAMPLE_NAME + "123")
                .withNamespace(SAMPLE_NAMESPACE).addToLabels(DEPLOYMENT_LABEL_NAME, SAMPLE_NAME + "-db").endMetadata()
                .editOrNewSpec().addNewContainer().endContainer().endSpec()
                .editOrNewSpec().addNewInitContainer().endInitContainer().endSpec()
                .withStatus(status).build());
    }

}
