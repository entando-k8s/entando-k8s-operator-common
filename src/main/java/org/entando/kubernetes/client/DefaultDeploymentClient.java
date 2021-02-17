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
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient, PodWaitingClient {

    private final KubernetesClient client;
    private BlockingQueue<PodWatcher> podWatcherHolder = new ArrayBlockingQueue<>(15);

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public boolean supportsStartupProbes() {
        final VersionInfo version = client.getVersion();
        //Is null when using the MockServer. Return true because that is the most common scenario we want to test
        return version == null || parseVersion(version) >= 16;
    }

    private int parseVersion(VersionInfo version) {
        StringBuilder sb = new StringBuilder();
        //Some versions have trailing non-digit characters
        for (char current : version.getMinor().toCharArray()) {
            if (Character.isDigit(current)) {
                sb.append(current);
            } else {
                break;
            }
        }
        return Integer.parseInt(sb.toString());
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace,
            Deployment deployment) {
        Deployment existingDeployment = getDeploymenResourceFor(peerInNamespace, deployment).get();
        if (existingDeployment == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            //Don't wait because watching the pods until they have been removed is safer than to Fabric8's polling
            getDeploymenResourceFor(peerInNamespace, deployment).scale(0, false);
            FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podResource = client.pods()
                    .inNamespace(existingDeployment.getMetadata().getNamespace())
                    .withLabelSelector(existingDeployment.getSpec().getSelector());
            if (!podResource.list().getItems().isEmpty()) {
                watchPod(pod -> podResource.list().getItems().isEmpty(), EntandoOperatorConfig.getPodShutdownTimeoutSeconds(),
                        podResource);
            }
            //Create the deployment with the correct replicas now. We don't support 0 because we will be waiting for the pod
            return getDeploymenResourceFor(peerInNamespace, deployment).patch(deployment);
        }
    }

    private RollableScalableResource<Deployment, DoneableDeployment> getDeploymenResourceFor(
            EntandoCustomResource peerInNamespace,
            Deployment deployment) {
        return client.apps()
                .deployments()
                .inNamespace(peerInNamespace.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName());
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

    @Override
    public BlockingQueue<PodWatcher> getPodWatcherQueue() {
        return podWatcherHolder;
    }
}
