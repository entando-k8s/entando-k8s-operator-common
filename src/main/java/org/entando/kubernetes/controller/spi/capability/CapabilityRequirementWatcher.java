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

package org.entando.kubernetes.controller.spi.capability;

import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.entando.kubernetes.model.EntandoDeploymentPhase;

public class CapabilityRequirementWatcher implements Watcher<CapabilityRequirement> {

    final Object mutex;
    private CapabilityRequirement capabiltyRequirement;
    private long started = System.currentTimeMillis();
    private boolean failed;

    public CapabilityRequirementWatcher(Object mutex) {
        this.mutex = mutex;
    }

    @Override
    public void eventReceived(Action action, CapabilityRequirement resource) {
        this.capabiltyRequirement = resource;
        if (hasCompleted()) {
            synchronized (mutex) {
                mutex.notifyAll();
            }
        }
    }

    private boolean hasCompleted() {
        if (capabiltyRequirement == null) {
            return false;
        }
        return capabiltyRequirement.getStatus().getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL
                || capabiltyRequirement.getStatus().getEntandoDeploymentPhase() == EntandoDeploymentPhase.FAILED;
    }

    @Override
    public void onClose(WatcherException cause) {
        failed = true;
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    public boolean shouldStillWait() {
        return !(hasTimedOut() || hasFailed() || hasCompleted());
    }

    public boolean hasTimedOut() {
        return System.currentTimeMillis() - started > 10 * 60 * 1000;
    }

    public boolean hasFailed() {
        return failed || (capabiltyRequirement != null
                && capabiltyRequirement.getStatus().getEntandoDeploymentPhase() == EntandoDeploymentPhase.FAILED);
    }
}
