package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import java.util.function.Predicate;

public class PodWatcher implements Watcher<Pod> {

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
