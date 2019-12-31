package org.entando.kubernetes.controller.test.support;

import static org.entando.kubernetes.controller.PodResult.SUCCEEDED_PHASE;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;

public interface PodBehavior {

    default Pod podWithReadyStatus(Deployment deployment) {
        return podWithReadyStatus(podFrom(deployment));
    }

    default Pod podWithReadyStatus(Pod pod) {
        PodStatus status = new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .withContainerStatuses(statusesFor(pod.getSpec().getContainers()))
                .withInitContainerStatuses(statusesFor(pod.getSpec().getInitContainers()))
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
        pod.setStatus(status);
        return pod;
    }

    default Pod podWithSucceededStatus(Pod pod) {
        PodStatus status = new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .withContainerStatuses(statusesFor(pod.getSpec().getContainers()))
                .withInitContainerStatuses(statusesFor(pod.getSpec().getInitContainers()))
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
        pod.setStatus(status);
        return pod;
    }

    default Pod podWithSucceededStatus(Deployment deployment) {
        return podWithSucceededStatus(podFrom(deployment));
    }

    default Pod podFrom(Deployment deployment) {
        PodTemplateSpec template = deployment.getSpec().getTemplate();
        return new PodBuilder()
                .withNewMetadata()
                .withLabels(template.getMetadata().getLabels())
                .withNamespace(deployment.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName() + "-" + RandomStringUtils.randomAlphanumeric(8))
                .endMetadata()
                .withNewSpec()
                .withContainers(template.getSpec().getContainers())
                .withInitContainers(template.getSpec().getInitContainers())
                .endSpec()
                .build();
    }

    default List<ContainerStatus> statusesFor(List<Container> initContainers) {
        return initContainers.stream()
                .map(container -> new ContainerStatusBuilder().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                        .endState().build()).collect(Collectors.toList());
    }

    default Pod podWithStatus(PodStatus status, String namespace) {
        return getClient().pods().start(new PodBuilder().withNewMetadata()
                .withName("pod-" + RandomStringUtils.randomAlphanumeric(8))
                .withNamespace(namespace)
                .endMetadata()
                .editOrNewSpec().addNewContainer().endContainer().endSpec()
                .editOrNewSpec().addNewInitContainer().endInitContainer().endSpec()
                .withStatus(status).build());
    }

    SimpleK8SClient<?> getClient();

}
