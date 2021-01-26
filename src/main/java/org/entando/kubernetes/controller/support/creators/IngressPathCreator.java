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

package org.entando.kubernetes.controller.support.creators;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.container.IngressingPathOnPort;
import org.entando.kubernetes.controller.spi.deployable.Ingressing;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class IngressPathCreator {

    private final EntandoCustomResource entandoCustomResource;

    public IngressPathCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public List<HTTPIngressPath> buildPaths(IngressingDeployable<?, ?> ingressingDeployable, Service service) {
        return ingressingDeployable.getIngressingContainers().stream().map(o -> newHttpPath(o, service)).collect(Collectors
                .toList());
    }

    public Ingress addMissingHttpPaths(IngressClient ingressClient, Ingressing<?> ingressingDeployable, Ingress ingress, Service service) {
        List<IngressingPathOnPort> ingressingContainers = ingressingDeployable.getIngressingContainers().stream()
                .filter(path -> this.httpPathIsAbsent(ingress, path))
                .collect(Collectors.toList());
        for (IngressingPathOnPort ingressingContainer : ingressingContainers) {
            ingressClient.addHttpPath(ingress, newHttpPath(ingressingContainer, service), Collections
                    .singletonMap(entandoCustomResource.getMetadata().getName() + "-path", ingressingContainer.getWebContextPath()));

        }
        return ingressClient.loadIngress(ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
    }

    private HTTPIngressPath newHttpPath(IngressingPathOnPort ingressingPathOnPort, Service service) {
        return new HTTPIngressPathBuilder()
                .withPath(ingressingPathOnPort.getWebContextPath())
                .withNewBackend()
                .withServiceName(service.getMetadata().getName())
                .withNewServicePort(ingressingPathOnPort.getPortForIngressPath())
                .endBackend()
                .build();
    }

    private boolean httpPathIsAbsent(Ingress ingress, IngressingPathOnPort ingressingContainer) {
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                .noneMatch(httpIngressPath -> httpIngressPath.getPath().equals(ingressingContainer.getWebContextPath()));
    }

}
