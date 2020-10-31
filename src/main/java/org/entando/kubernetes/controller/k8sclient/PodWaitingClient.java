/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.k8sclient;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.client.PodWatcher;

public interface PodWaitingClient {

    /**
     * A getter for the an AtomicReference to the most recently constructed PodWatcherholder for testing purposes.
     */
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
                        mutex.wait( 1000);
                    }
                    if (watcher.podResourceFulfillsCondition()) {
                        return watcher.getLastPod();
                    }
                    if (watcher.getLastPod() != null) {
                        throw new IllegalStateException(format("Pod %s/%s did not meet the wait condition within %s seconds",
                                watcher.getLastPod().getMetadata().getNamespace(),
                                watcher.getLastPod().getMetadata().getName(),
                                timeoutSeconds));
                    } else {
                        throw new IllegalStateException(format("Podresource did not meet the wait condition within %s seconds",
                                timeoutSeconds));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
