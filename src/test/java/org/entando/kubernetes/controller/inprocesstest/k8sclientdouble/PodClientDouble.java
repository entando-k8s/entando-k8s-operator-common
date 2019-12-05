package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import java.util.Date;
import java.util.Map;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class PodClientDouble extends AbstractK8SClientDouble implements PodClient {

    public PodClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Pod runToCompletion(EntandoCustomResource resource, Pod pod) {
        pod.setStatus(new PodStatusBuilder().withPhase("Complete").build());
        pod.getSpec().getInitContainers().forEach(container -> pod.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                .build()));
        pod.getSpec().getContainers().forEach(container -> pod.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                .build()));
        return pod;
    }

    @Override
    public Pod start(Pod pod) {
        getNamespace(pod).putPod(pod);
        return pod;
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        Pod result = getNamespace(namespace).getPods().values().stream()
                .filter(pod -> labelValue.equals(pod.getMetadata().getLabels().get(labelName))).findFirst()
                .orElseThrow(() ->
                        new IllegalStateException());
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
}
