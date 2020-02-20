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
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.controller.k8sclient.PodWaitingClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient, PodWaitingClient {

    private final KubernetesClient client;
    private AtomicReference<PodWatcher> podWatcherHolder = new AtomicReference<>();

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace, Deployment deployment) {
        RollableScalableResource<Deployment, DoneableDeployment> resource = client.apps()
                .deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(deployment.getMetadata().getName());
        Deployment existingDeployment = resource.get();
        if (existingDeployment == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            resource.scale(0, true);
            FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podResource = client.pods()
                    .inNamespace(existingDeployment.getMetadata().getNamespace())
                    .withLabelSelector(existingDeployment.getSpec().getSelector());
            if (!podResource.list().getItems().isEmpty()) {
                watchPod(pod -> podResource.list().getItems().isEmpty(), EntandoOperatorConfig.getPodCompletionTimeoutSeconds(),
                        podResource);
            }
            resource.patch(deployment);
            return resource.scale(Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(1), true);
        }
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

    @Override
    public AtomicReference<PodWatcher> getPodWatcherHolder() {
        return podWatcherHolder;
    }
}
