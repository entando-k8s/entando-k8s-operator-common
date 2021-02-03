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

package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.entando.kubernetes.client.EntandoExecListener;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.integrationtest.support.PodResourceDouble;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;

public class PodClientDouble extends AbstractK8SClientDouble implements PodClient {

    private final BlockingQueue<PodWatcher> podWatcherHolder = new ArrayBlockingQueue<>(15);
    private final BlockingQueue<EntandoExecListener> execListenerHolder = new ArrayBlockingQueue<>(15);

    public PodClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public BlockingQueue<PodWatcher> getPodWatcherQueue() {
        return podWatcherHolder;
    }

    @Override
    public BlockingQueue<EntandoExecListener> getExecListenerHolder() {
        return execListenerHolder;
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels) {
        getNamespace(namespace).getPods().values().removeIf(
                pod -> labels.entrySet().stream()
                        .allMatch(labelEntry -> pod.getMetadata().getLabels().containsKey(labelEntry.getKey()) && pod.getMetadata()
                                .getLabels().get(
                                        labelEntry.getKey()).equals(labelEntry.getValue())));
        if (ENQUEUE_POD_WATCH_HOLDERS.get()) {
            watchPod(Objects::isNull,
                    EntandoOperatorConfig.getPodShutdownTimeoutSeconds(), new DummyWatchable());

        }

    }

    @Override
    public Pod runToCompletion(Pod pod) {
        if (pod != null) {
            getNamespace(pod).putPod(pod);
            if (ENQUEUE_POD_WATCH_HOLDERS.get()) {
                return watchPod(got -> PodResult.of(got).getState() == State.COMPLETED,
                        EntandoOperatorConfig.getPodCompletionTimeoutSeconds(), new DummyWatchable());

            } else {

                pod.setStatus(new PodStatusBuilder().withPhase("Complete").build());
                pod.getSpec().getInitContainers()
                        .forEach(container -> pod.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                                .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                                .build()));
                pod.getSpec().getContainers().forEach(container -> pod.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                        .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                        .build()));
            }
        }
        return pod;
    }

    @Override
    public ExecWatch executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) {
        if (pod != null) {
            PodResource<Pod, DoneablePod> podResource = new PodResourceDouble();
            return executeAndWait(podResource, containerName, timeoutSeconds, commands);
        }
        return null;
    }

    @Override
    public Pod start(Pod pod) {
        getNamespace(pod).putPod(pod);
        return pod;
    }

    @Override
    public Pod loadPod(String namespace, Map<String, String> labels) {
        return getNamespace(namespace).getPods().values().stream()
                .filter(pod -> labels.entrySet().stream().allMatch(stringStringEntry -> stringStringEntry.getValue()
                        .equals(pod.getMetadata().getLabels().get(stringStringEntry.getKey())))).findFirst().orElse(null);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        if (ENQUEUE_POD_WATCH_HOLDERS.get()) {
            Pod result = watchPod(
                    got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                    EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                    new DummyWatchable());
            return result;
        } else if (!getNamespace(namespace).getPods().isEmpty()) {
            Pod result = getNamespace(namespace).getPods().values().stream()
                    .filter(pod -> labelValue.equals(pod.getMetadata().getLabels().get(labelName))).findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(format("Could not find pod with label %s=%s", labelName, labelValue)));
            result.setStatus(new PodStatusBuilder().withPhase("Running").build());
            if (result.getSpec() == null) {
                result.setSpec(new PodSpec());
                result.getSpec().getContainers().add(new Container());
            }
            result.getSpec().getInitContainers()
                    .forEach(container -> result.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewRunning().endRunning().endState()
                            .build()));
            result.getSpec().getContainers()
                    .forEach(container -> result.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewRunning().endRunning().endState()
                            .build()));
            result.getStatus().getConditions()
                    .add(new PodConditionBuilder().withType("Ready")
                            .withLastTransitionTime(PodResult.DATE_FORMAT.get().format(new Date()))
                            .build());
            return result;
        }
        return null;
    }

    private static class DummyWatchable implements Watchable<Watch, Watcher<Pod>> {

        @Override
        public Watch watch(Watcher<Pod> podWatcher) {
            return () -> {
            };
        }

        @Override
        public Watch watch(String s, Watcher<Pod> podWatcher) {
            return () -> {
            };
        }
    }
}
