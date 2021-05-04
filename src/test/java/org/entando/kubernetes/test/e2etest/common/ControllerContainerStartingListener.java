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

package org.entando.kubernetes.test.e2etest.common;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Optional;
import org.entando.kubernetes.controller.support.controller.ControllerExecutor;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;

public class ControllerContainerStartingListener<R extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> {

    protected final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> operations;
    private boolean shouldListen = true;
    private Watch watch;

    public ControllerContainerStartingListener(MixedOperation<R, KubernetesResourceList<R>, Resource<R>> operations) {
        this.operations = operations;
    }

    public void stopListening() {
        shouldListen = false;
        if (watch != null) {
            watch.close();
            watch = null;
        }
    }

    public void listen(String namespace, ControllerExecutor executor, String imageVersionToUse) {
        this.watch = operations.inNamespace(namespace).watch(new Watcher<R>() {
            @Override
            public void eventReceived(Action action, R resource) {
                if (shouldListen && action == Action.ADDED) {
                    try {
                        System.out.println("!!!!!!!On " + resource.getKind() + " add!!!!!!!!!");
                        executor.startControllerFor(action, resource, imageVersionToUse);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(WatcherException cause) {
                Optional.ofNullable(cause).ifPresent(Throwable::printStackTrace);
            }
        });
    }

}
