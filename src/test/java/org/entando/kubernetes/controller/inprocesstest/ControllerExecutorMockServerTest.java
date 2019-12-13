package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Pod;
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

@Tag("in-process")
@EnableRuleMigrationSupport
public class ControllerExecutorMockServerTest extends ControllerExecutorTestBase {

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
    protected SimpleK8SClient<?> getClient() {
        return new DefaultSimpleK8SClient(server.getClient());
    }

    @Override
    protected void emulatePodWaitingBehaviour() {
        new Thread(() -> {
            DefaultPodClient.setPodWatcherHolder(podWatcherHolder);
            await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
            Pod pod = server.getClient().pods()
                    .inNamespace(CONTROLLER_NAMESPACE)
                    .withLabel(resource.getKind(), resource.getMetadata().getName())
                    .list().getItems().get(0);
            pod.setStatus(succeededPodStatus());
            currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, pod);
        }).start();
    }

}
