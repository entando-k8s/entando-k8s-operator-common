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

package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.util.Optional;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class ControllerContainerStartingListener<
        R extends EntandoBaseCustomResource<?>,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<R, D>
        > {

    protected final CustomResourceOperationsImpl<R, L, D> operations;
    private boolean shouldListen = true;
    private Watch watch;

    public ControllerContainerStartingListener(CustomResourceOperationsImpl<R, L, D> operations) {
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
            @SuppressWarnings("unchecked")
            public void eventReceived(Action action, R resource) {
                if (shouldListen && action == Action.ADDED) {
                    try {
                        System.out.println("!!!!!!!On " + resource.getKind() + " add!!!!!!!!!");
                        executor.startControllerFor(action, (EntandoBaseCustomResource<? extends EntandoDeploymentSpec>) resource,
                                imageVersionToUse);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                Optional.ofNullable(cause).ifPresent(Throwable::printStackTrace);
            }
        });
    }

}
