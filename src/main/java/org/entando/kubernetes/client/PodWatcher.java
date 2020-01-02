package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import java.util.function.Predicate;

public class PodWatcher implements Watcher<Pod> {

    private final Predicate<Pod> podPredicate;
    private final Object mutex;
    private final long timeout;
    private final long startTime = System.currentTimeMillis();
    private Pod lastPod;

    public PodWatcher(Predicate<Pod> podPredicate, Object mutex, long timeout) {
        this.podPredicate = pod -> {
            try {
                return podPredicate.test(pod);
            } catch (Exception e) {
                return false;
            }
        };
        this.mutex = mutex;
        this.timeout = timeout;
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

    public boolean shouldStillWait() {
        return !(hasTimedOut() || lastPodFulfillsCondition());
    }

    public boolean lastPodFulfillsCondition() {
        return lastPod != null && podPredicate.test(lastPod);
    }

    private boolean hasTimedOut() {
        return System.currentTimeMillis() > startTime + timeout;
    }
}
