package org.entando.kubernetes.controller.k8sclient;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.client.PodWatcher;

public interface PodClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod loadPod(String namespace, String labelName, String labelValue);

    Pod runToCompletion(Pod pod);

    AtomicReference<PodWatcher> getPodWatcherHolder();

    default Pod watchPod(Predicate<Pod> podPredicate, long timeoutSeconds, Watchable<Watch, Watcher<Pod>> podResource) {
        try {
            Object mutex = new Object();

            synchronized (mutex) {
                PodWatcher watcher = new PodWatcher(podPredicate, mutex, timeoutSeconds * 1000);
                getPodWatcherHolder().set(watcher);
                try (Watch ignored = podResource.watch(watcher)) {
                    //Sonar seems to believe the JVM may not respect wait() with timeout due to 'Spurious wakeups'
                    while (watcher.shouldStillWait()) {
                        mutex.wait(timeoutSeconds * 1000);
                    }
                    if (watcher.lastPodFulfillsCondition()) {
                        return watcher.getLastPod();
                    }
                    throw new IllegalStateException(format("Pod %s/%s did not meet the wait condition within %s seconds",
                            watcher.getLastPod().getMetadata().getNamespace(),
                            watcher.getLastPod().getMetadata().getName(),
                            String.valueOf(timeoutSeconds)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
