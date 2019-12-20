package org.entando.kubernetes.controller.k8sclient;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface PodClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod loadPod(String namespace, String labelName, String labelValue);

    Pod runToCompletion(EntandoCustomResource resource, Pod pod);

    AtomicReference<PodWatcher> getPodWatcherHolder();

    default Pod watchPod(Predicate<Pod> podPredicate, long timeoutSeconds, Watchable<Watch, Watcher<Pod>> podResource) {
        try {
            Object mutex = new Object();

            synchronized (mutex) {
                PodWatcher watcher = new PodWatcher(podPredicate, mutex);
                getPodWatcherHolder().set(watcher);
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

}
