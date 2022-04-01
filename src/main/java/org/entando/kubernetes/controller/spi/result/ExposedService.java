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

package org.entando.kubernetes.controller.spi.result;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackend;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;

public class ExposedService extends AbstractServiceResult {

    protected final Ingress ingress;

    public ExposedService(Service service, Ingress ingress) {
        super(service);
        this.ingress = ingress;
    }

    public String getExternalBaseUrlForPort(String portName) {
        return getExternalHostUrl() + getHttpIngressPathForPort(portName).getPath();
    }

    public String getInternalBaseUrlForPort(String portName) {
        HTTPIngressPath pathForPort = getHttpIngressPathForPort(portName);
        return "http://" + getInternalServiceHostname() + ":" + pathForPort.getBackend().getService().getPort()
                .getNumber() + pathForPort
                .getPath();
    }

    public String getExternalHostUrl() {
        String protocol = isTlsEnabled() || EntandoOperatorSpiConfig.assumeExternalHttpsProvider() ? "https" : "http";
        return protocol + "://" + ingress.getSpec().getRules().get(0).getHost();
    }

    public String getExternalBaseUrl() {
        return getExternalHostUrl() + getHttpIngressPath().getPath();
    }

    public String getInternalBaseUrl() {
        return "http://" + getInternalServiceHostname() + ":" + getPort() + getHttpIngressPath().getPath();
    }

    protected boolean isTlsEnabled() {
        return ofNullable(ingress.getSpec().getTls())
                .map(list -> !list.isEmpty())
                .orElse(false);
    }

    private HTTPIngressPath getHttpIngressPath() {
        if (hasMultiplePorts()) {
            throw new IllegalStateException("Cannot make assumption to use a port as there are multiple ports");
        }
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream().filter(this::matchesService)
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    private boolean hasMultiplePorts() {
        return service.getSpec().getPorts().size() > 1;
    }

    private HTTPIngressPath getHttpIngressPathForPort(String portName) {
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                .filter(path -> this.matchesServiceAndPortName(path, portName))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    private boolean matchesServiceAndPortName(HTTPIngressPath httpIngressPath, String portName) {
        IngressBackend backend = httpIngressPath.getBackend();
        return (matchesThisService(backend) || matchesDelegatingService(backend)) && hasMatchingServicePortNamed(backend, portName);
    }

    private boolean hasMatchingServicePortNamed(IngressBackend backend, String portName) {
        return service.getSpec().getPorts().stream()
                .anyMatch(servicePort -> portName.equals(servicePort.getName()) && backend.getService().getPort()
                        .getNumber()
                        .equals(servicePort.getPort()));
    }

    private boolean matchesService(HTTPIngressPath httpIngressPath) {
        IngressBackend backend = httpIngressPath.getBackend();
        return (matchesThisService(backend) || matchesDelegatingService(backend)) && hasMatchingServicePort(backend);
    }

    private boolean hasMatchingServicePort(IngressBackend backend) {
        return service.getSpec().getPorts().stream()
                .anyMatch(servicePort ->
                        backend.getService().getPort().getNumber().equals(
                                servicePort.getPort()));
    }

    private boolean matchesThisService(IngressBackend backend) {
        return backend.getService().getName().equals(service.getMetadata().getName());
    }

    private boolean matchesDelegatingService(IngressBackend backend) {
        return backend.getService().getName().endsWith("-to-" + service.getMetadata().getName());
    }

    @SerializeByReference
    public Ingress getIngress() {
        return ingress;
    }

}
