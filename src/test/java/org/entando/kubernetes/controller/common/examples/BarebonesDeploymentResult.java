package org.entando.kubernetes.controller.common.examples;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.spi.ServiceResult;

public class BarebonesDeploymentResult implements ServiceResult {

    private final Pod pod;

    public BarebonesDeploymentResult(Pod pod) {
        this.pod = pod;
    }

    @Override
    public String getInternalServiceHostname() {
        return pod.getStatus().getPodIP();
    }

    @Override
    public String getPort() {
        return pod.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort().toString();
    }

    @Override
    public Service getService() {
        return null;
    }
}
