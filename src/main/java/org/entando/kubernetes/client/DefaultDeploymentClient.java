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
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class DefaultDeploymentClient implements DeploymentClient, PodWaitingClient {

    private final KubernetesClient client;
    private AtomicReference<PodWatcher> podWatcherHolder = new AtomicReference<>();

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public <S extends EntandoDeploymentSpec> Deployment createOrPatchDeployment(EntandoBaseCustomResource<S> peerInNamespace,
            Deployment deployment) {
        Deployment existingDeployment = getDeploymenResourceFor(peerInNamespace, deployment).get();
        if (existingDeployment == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            getDeploymenResourceFor(peerInNamespace, deployment).scale(0, true);
            FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podResource = client.pods()
                    .inNamespace(existingDeployment.getMetadata().getNamespace())
                    .withLabelSelector(existingDeployment.getSpec().getSelector());
            if (!podResource.list().getItems().isEmpty()) {
                watchPod(pod -> podResource.list().getItems().isEmpty(), EntandoOperatorConfig.getPodCompletionTimeoutSeconds(),
                        podResource);
            }
            //Create the deployment with the correct replicas now. We don't support 0 because we will be waiting for the pod
            return getDeploymenResourceFor(peerInNamespace, deployment).patch(deployment);
        }
    }

    private <S extends EntandoDeploymentSpec> RollableScalableResource<Deployment, DoneableDeployment> getDeploymenResourceFor(
            EntandoBaseCustomResource<S> peerInNamespace,
            Deployment deployment) {
        return client.apps()
                .deployments()
                .inNamespace(peerInNamespace.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName());
    }

    @Override
    public <S extends EntandoDeploymentSpec> Deployment loadDeployment(EntandoBaseCustomResource<S> peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

    @Override
    public AtomicReference<PodWatcher> getPodWatcherHolder() {
        return podWatcherHolder;
    }
}
