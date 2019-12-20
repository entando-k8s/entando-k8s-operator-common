package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.controller.PodResult.State;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class PodClientDouble extends AbstractK8SClientDouble implements PodClient {

    private static boolean emulatePodWatching = false;
    private AtomicReference<PodWatcher> podWatcherHolder = new AtomicReference<>();

    public PodClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    public static void setEmulatePodWatching(boolean emulatePodWatching) {
        PodClientDouble.emulatePodWatching = emulatePodWatching;
    }

    @Override
    public AtomicReference<PodWatcher> getPodWatcherHolder() {
        return podWatcherHolder;
    }

    @Override
    public Pod runToCompletion(EntandoCustomResource resource, Pod pod) {
        if (emulatePodWatching) {
            if (pod != null) {
                return watchPod(got -> PodResult.of(got).getState() == State.COMPLETED,
                        EntandoOperatorConfig.getPodCompletionTimeoutSeconds(), new DummyWatchable());

            }
        } else if (pod != null) {
            pod.setStatus(new PodStatusBuilder().withPhase("Complete").build());
            pod.getSpec().getInitContainers()
                    .forEach(container -> pod.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                            .build()));
            pod.getSpec().getContainers().forEach(container -> pod.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                    .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                    .build()));
        }
        return pod;
    }

    @Override
    public Pod start(Pod pod) {
        getNamespace(pod).putPod(pod);
        return pod;
    }

    @Override
    public Pod loadPod(String namespace, String labelName, String labelValue) {
        return getNamespace(namespace).getPods().values().stream()
                .filter(pod -> labelValue.equals(pod.getMetadata().getLabels().get(labelName))).findFirst().orElse(null);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        if (emulatePodWatching) {
            Pod result = getNamespace(namespace).getPods().values().stream()
                    .filter(pod -> labelValue.equals(pod.getMetadata().getLabels().get(labelName))).findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(format("Could not find pod with label %s=%s", labelName, labelValue)));
            PodStatus status = watchPod(
                    got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                    EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                    new DummyWatchable()).getStatus();
            if (result != null) {
                result.setStatus(status);
            }
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
            result.getSpec().getContainers().forEach(container -> result.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                    .withNewState().withNewRunning().endRunning().endState()
                    .build()));
            result.getStatus().getConditions()
                    .add(new PodConditionBuilder().withType("Ready").withLastTransitionTime(PodResult.DATE_FORMAT.get().format(new Date()))
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
