package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.controller.PodResult.State;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultPodClient implements PodClient {

    private AtomicReference<PodWatcher> podWatcherHolder = new AtomicReference<>();
    private final KubernetesClient client;

    public DefaultPodClient(KubernetesClient client) {
        this.client = client;
        //HACK for GraalVM
        KubernetesDeserializer.registerCustomKind("v1", "Pod", Pod.class);
    }

    @Override
    public AtomicReference<PodWatcher> getPodWatcherHolder() {
        return podWatcherHolder;
    }

    @Override
    public Pod runToCompletion(EntandoCustomResource resource, Pod pod) {
        String namespace = resource.getMetadata().getNamespace();
        Pod running = this.client.pods().inNamespace(namespace).create(pod);
        return waitFor(running, got -> PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodCompletionTimeoutSeconds());
    }

    @Override
    public Pod start(Pod pod) {
        return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(namespace).withLabel(labelName, labelValue);
        return watchPod(got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                watchable);
    }

    @Override
    public Pod loadPod(String namespace, String labelName, String labelValue) {
        return client.pods().inNamespace(namespace).withLabel(labelName, labelValue).list().getItems().stream().findFirst().orElse(null);
    }

    /**
     * For some reason a local Openshift consistently resulted in timeouts on pod.waitUntilCondition(), so we had to implement our own
     * logic. waituntilCondition also polls which is nasty.
     */
    private Pod waitFor(Pod pod, Predicate<Pod> podPredicate, long timeoutSeconds) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return watchPod(podPredicate, timeoutSeconds, watchable);

    }
}
