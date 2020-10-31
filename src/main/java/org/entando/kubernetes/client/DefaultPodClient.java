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

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.controller.PodResult.State;
import org.entando.kubernetes.controller.k8sclient.PodClient;

public class DefaultPodClient implements PodClient {

    private final KubernetesClient client;
    private AtomicReference<PodWatcher> podWatcherHolder = new AtomicReference<>();
    private AtomicReference<EntandoExecListener> execListenerHolder = new AtomicReference<>();

    public DefaultPodClient(KubernetesClient client) {
        this.client = client;
        //HACK for GraalVM
        KubernetesDeserializer.registerCustomKind("v1", "Pod", Pod.class);
    }

    public AtomicReference<EntandoExecListener> getExecListenerHolder() {
        return execListenerHolder;
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels) {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podResource = client
                .pods().inNamespace(namespace).withLabels(labels);
        podResource.delete();
        watchPod(
                pod -> podResource.list().getItems().isEmpty(),
                EntandoOperatorConfig.getPodShutdownTimeoutSeconds(), podResource);
        System.out.println("Removed successfully");
    }

    @Override
    public AtomicReference<PodWatcher> getPodWatcherHolder() {
        return podWatcherHolder;
    }

    @Override
    public Pod runToCompletion(Pod pod) {
        Pod running = this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
        return waitFor(running, got -> PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodCompletionTimeoutSeconds());
    }

    @Override
    public ExecWatch executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) {
        PodResource<Pod, DoneablePod> podResource = this.client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return executeAndWait(podResource, containerName, timeoutSeconds, commands);
    }

    @Override
    public Pod start(Pod pod) {
        return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(namespace).withLabel(labelName, labelValue);
        return watchPod(got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                watchable);
    }

    @Override
    public Pod loadPod(String namespace, String labelName, String labelValue) {
        return client.pods().inNamespace(namespace).withLabel(labelName, labelValue).list().getItems().stream().findFirst().orElse(null);
    }

    /**
     * For some reason a local Openshift consistently resulted in timeouts on pod.waitUntilCondition(), so we had to implement our own
     * logic. waituntilCondition also polls which is nasty.
     */
    private Pod waitFor(Pod pod, Predicate<Pod> podPredicate, long timeoutSeconds) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return watchPod(podPredicate, timeoutSeconds, watchable);

    }

}

