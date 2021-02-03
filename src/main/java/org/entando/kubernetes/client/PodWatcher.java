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
        this.mutex = mutex;
        this.podPredicate = pod -> {
            try {
                return podPredicate.test(pod);
            } catch (Exception e) {
                return false;
            }
        };
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
        return !(hasTimedOut() || podResourceFulfillsCondition());
    }

    public boolean podResourceFulfillsCondition() {
        try {
            return podPredicate.test(lastPod);
        } catch (NullPointerException npe) {
            //Ignore NPEs and treat them as false
            return false;
        }
    }

    private boolean hasTimedOut() {
        return System.currentTimeMillis() > startTime + timeout;
    }
}
