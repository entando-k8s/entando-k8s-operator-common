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

package org.entando.kubernetes.controller.support.client.impl;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;

import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.PodClient;

public class DefaultPodClient implements PodClient {

    private static final Logger LOGGER = Logger.getLogger(DefaultPodClient.class.getName());

    private final KubernetesClient client;

    public DefaultPodClient(KubernetesClient client) {
        this.client = client;
        //HACK for GraalVM
        //KubernetesDeserializer.registerCustomKind("v1", "Pod", Pod.class);
        KubernetesDeserializer deserializer = new KubernetesDeserializer();
        deserializer.registerCustomKind("v1", "Pod", Pod.class);
    }

    @Override
    public void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels) {
        client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream()
                .filter(pod -> PodResult.of(pod).getState() == State.COMPLETED && !PodResult.of(pod).hasFailed())
                .forEach(client.pods().inNamespace(namespace)::delete);
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels, int timeoutSeconds) throws TimeoutException {
        interruptionSafe(() -> {
            FilterWatchListDeletable<Pod, PodList, PodResource> podResource = client.pods().inNamespace(namespace).withLabels(labels);
            podResource.delete();
            return waitUntilCondition(
                    podResource,
                    // FABRIC8 6.x MIGRATION:
                    // OLD CODE
                    //pod -> podResource.list().getItems().isEmpty(),
                    Objects::isNull,  // null means the list is empty
                    timeoutSeconds,
                    TimeUnit.SECONDS
            );
        });
    }

    @Override
    public Pod runToCompletion(Pod pod, int timeoutSeconds) throws TimeoutException {
        return interruptionSafe(() -> {
            this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
            return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName())
                    .waitUntilCondition(got -> PodResult.of(got).getState() == State.COMPLETED || hasPodFailedContainers(got),
                            timeoutSeconds, TimeUnit.SECONDS);
        });
    }

    private boolean hasPodFailedContainers(Pod pod) {
        for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
            ContainerStateWaiting waiting = cs.getState().getWaiting();
            if (waiting != null && waiting.getReason().toLowerCase(Locale.ROOT).contains("error")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void deletePod(Pod pod) {
        this.client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).delete();
    }

    @Override
    public Pod start(Pod pod) {
        return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue, int timeoutSeconds) throws TimeoutException {
        String shortLabelValue = NameUtils.shortenLabelToMaxLength(labelValue);
        return interruptionSafe(() -> waitUntilCondition(
                client.pods().inNamespace(namespace).withLabel(labelName, shortLabelValue),
                isPodUpAndRunning(),
                EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds(),
                TimeUnit.SECONDS
        ));

    }

    private Predicate<Pod> isPodUpAndRunning() {
        return got -> got != null && got.getStatus() != null && (PodResult.of(got).getState() == State.READY
                || PodResult.of(got).getState() == State.COMPLETED);
    }

    @Override
    public Pod loadPod(String namespace, Map<String, String> labels) {
        return client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream().findFirst().orElse(null);
    }

    /**
     * Handle timeout for a CompletableFuture by canceling it and logging a warning.
     * This avoids blocking calls on the event loop.
     *
     * @param futureCondition the future to check for timeout
     * @param amount timeout amount
     * @param timeUnit timeout unit
     * @return true if the future completed successfully, false if it timed out
     */
    private static boolean handleTimeout(CompletableFuture<?> futureCondition, long amount, TimeUnit timeUnit) {
        if (!Utils.waitUntilReady(futureCondition, amount, timeUnit)) {
            futureCondition.cancel(true);
            // Don't make blocking call to list() - the informer's cache was already checked via informOnCondition
            LOGGER.log(Level.WARNING, () -> "Timed out waiting for resource condition");
            return false;
        }
        return true;
    }

    public static Pod waitUntilCondition(
            FilterWatchListDeletable<Pod, PodList, PodResource> informable,
            Predicate<Pod> condition, long amount, TimeUnit timeUnit
    ) {
        CompletableFuture<List<Pod>> futureCondition = informable.informOnCondition(l -> {
            if (l.isEmpty()) {
                return condition.test(null);
            }
            return condition.test(l.get(0));
        });

        if (!handleTimeout(futureCondition, amount, timeUnit)) {
            return null;
        }
        return futureCondition.thenApply(l -> l.isEmpty() ? null : l.get(0)).getNow(null);
    }

    public static CustomResourceDefinition waitUntilCondition(
            NonNamespaceOperation<CustomResourceDefinition, CustomResourceDefinitionList, Resource<CustomResourceDefinition>> informable,
            Predicate<CustomResourceDefinition> condition, long amount, TimeUnit timeUnit
    ) {
        CompletableFuture<List<CustomResourceDefinition>> futureCondition = informable.informOnCondition(l -> {
            if (l.isEmpty()) {
                return condition.test(null);
            }
            return condition.test(l.get(0));
        });

        if (!handleTimeout(futureCondition, amount, timeUnit)) {
            return null;
        }
        return futureCondition.thenApply(l -> l.isEmpty() ? null : l.get(0)).getNow(null);
    }

    /**
     * Wait until a condition on the entire pod list is met, without blocking the event loop.
     * This method uses informOnCondition to watch the informer's cache and avoids blocking calls.
     *
     * @param informable the pod resource to watch
     * @param listCondition predicate that tests the entire list of pods
     * @param amount timeout amount
     * @param timeUnit timeout unit
     * @return true if condition was met, false if timed out
     */
    public static boolean waitUntilConditionOnList(
            FilterWatchListDeletable<Pod, PodList, PodResource> informable,
            Predicate<List<Pod>> listCondition, long amount, TimeUnit timeUnit
    ) {
        CompletableFuture<List<Pod>> futureCondition = informable.informOnCondition(listCondition);
        return handleTimeout(futureCondition, amount, timeUnit);
    }

}

