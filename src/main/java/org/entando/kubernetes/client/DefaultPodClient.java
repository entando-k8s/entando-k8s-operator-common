package org.entando.kubernetes.client;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.controller.PodResult.State;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultPodClient implements PodClient {

    private final DefaultKubernetesClient client;

    public DefaultPodClient(DefaultKubernetesClient client) {
        this.client = client;
    }

    @Override
    public Pod runToCompletion(EntandoCustomResource resource, Pod pod) {
        String namespace = resource.getMetadata().getNamespace();
        Pod running = this.client.pods().inNamespace(namespace).create(pod);
        return waitFor(running, got -> PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodCompletionTimeoutSeconds());
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        Watchable<Watch, Watcher<Pod>> watchable = client
                .pods().inNamespace(namespace).withLabel(labelName, labelValue);
        return watchPod(got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                watchable);
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

    private Pod watchPod(Predicate<Pod> podPredicate, long timeoutSeconds, Watchable<Watch, Watcher<Pod>> podResource) {
        try {
            Object mutex = new Object();

            synchronized (mutex) {
                PodWatcher watcher = new PodWatcher(podPredicate, mutex);
                podResource.watch(watcher);
                mutex.wait(timeoutSeconds * 1000);
                Pod got = watcher.getLastPod();
                if (podPredicate.test(got)) {
                    return got;
                }
                throw new IllegalStateException(format("Pod %s/%s did not meet the wait condition within %s seconds",
                        got.getMetadata().getNamespace(),
                        got.getMetadata().getName(),
                        String.valueOf(timeoutSeconds)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static class PodWatcher implements Watcher<Pod> {

        private final Predicate<Pod> podPredicate;
        private final Object mutex;
        private Pod lastPod;

        public PodWatcher(Predicate<Pod> podPredicate, Object mutex) {
            this.podPredicate = pod -> {
                try {
                    return podPredicate.test(pod);
                } catch (Exception e) {
                    return false;
                }
            };
            this.mutex = mutex;
        }

        @Override
        public void eventReceived(Action action, Pod resource) {
            lastPod = resource;
            if (podPredicate.test(resource)) {
                synchronized (mutex) {
                    mutex.notifyAll();
                }
            }
        }

        @Override
        public void onClose(KubernetesClientException cause) {
            synchronized (mutex) {
                mutex.notifyAll();
            }
        }

        public Pod getLastPod() {
            return lastPod;
        }
    }
}
