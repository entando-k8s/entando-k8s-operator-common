package org.entando.kubernetes.controller;

import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.spi.ServiceResult;

public abstract class AbstractServiceResult implements ServiceResult {

    protected Service service;

    public AbstractServiceResult(Service service) {
        this.service = service;
    }

    @Override
    public String getInternalServiceHostname() {
        return service.getMetadata().getName() + "." + service.getMetadata().getNamespace() + ".svc.cluster.local";
    }

    @Override
    public String getPort() {
        return service.getSpec().getPorts().get(0).getPort().toString();
    }

    @Override
    public Service getService() {
        return service;
    }

}
