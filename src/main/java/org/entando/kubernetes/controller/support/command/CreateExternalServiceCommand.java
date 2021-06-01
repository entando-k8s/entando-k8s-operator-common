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

package org.entando.kubernetes.controller.support.command;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.entando.kubernetes.controller.spi.common.ExceptionUtils;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.deployable.ExternalService;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.common.FluentTernary;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.InternalServerStatus;

public class CreateExternalServiceCommand {

    private static final int TCP4_NUMBER_OF_BYTES = 4;
    private static final int TCP6_NUMBER_OF_SEGMENTS = 8;
    private final ExternalService externalService;
    private final EntandoCustomResource entandoCustomResource;
    private final InternalServerStatus status = new InternalServerStatus();

    public CreateExternalServiceCommand(ExternalService externalService, EntandoCustomResource entandoCustomResource) {
        this.externalService = externalService;
        this.entandoCustomResource = entandoCustomResource;
        status.setQualifier(NameUtils.MAIN_QUALIFIER);
    }

    public InternalServerStatus getStatus() {
        return status;
    }

    public Service execute(SimpleK8SClient<?> k8sClient) {
        try {
            Service service = k8sClient.services().createOrReplaceService(entandoCustomResource, newExternalService());
            maybeCreateEndpoints(k8sClient);
            this.status.setServiceName(service.getMetadata().getName());
            return service;
        } catch (Exception e) {
            this.status.finishWith(ExceptionUtils.failureOf(this.entandoCustomResource, e));
            return null;
        }
    }

    public Endpoints maybeCreateEndpoints(SimpleK8SClient<?> k8sClient) {
        Endpoints endpoints = null;
        if (isIpAddress()) {
            endpoints = newEndpoints();
            k8sClient.services().createOrReplaceEndpoints(entandoCustomResource, endpoints);
        }
        return endpoints;
    }

    private Endpoints newEndpoints() {
        return new EndpointsBuilder()
                //Needs to match the service name exactly
                .withMetadata(fromCustomResource())
                .addNewSubset()
                .addNewAddress()
                .withIp(externalService.getHost())
                .endAddress()
                .addNewPort()
                .withPort(externalService.getPort())
                .endPort()
                .endSubset()
                .build();
    }

    private Service newExternalService() {
        return new ServiceBuilder()
                .withMetadata(fromCustomResource())
                .withNewSpec()
                .withExternalName(FluentTernary.useNull(String.class).when(isIpAddress())
                        .orElse(externalService.getHost()))
                .withType(FluentTernary.use("ClusterIP").when(isIpAddress()).orElse("ExternalName"))
                .addNewPort()
                .withNewTargetPort(
                        externalService.getPort())
                .withPort(externalService.getPort())
                .endPort()
                .endSpec()
                .build();
    }

    private boolean isIpAddress() {
        String host = externalService.getHost();
        try {
            String[] split = host.split("\\.");
            if (split.length == TCP4_NUMBER_OF_BYTES) {
                for (String s : split) {
                    int i = Integer.parseInt(s);
                    if (i > 255 || i < 0) {
                        return false;
                    }
                }
                return true;
            } else {
                split = host.split("\\:");

                if (split.length == TCP6_NUMBER_OF_SEGMENTS) {
                    for (String s : split) {
                        Integer.parseInt(s, 16);
                    }
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private ObjectMeta fromCustomResource() {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(entandoCustomResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_SERVICE_SUFFIX)
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .addToLabels(LabelNames.RESOURCE_KIND.getName(), entandoCustomResource.getKind())
                .addToLabels(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName());
        metaBuilder = metaBuilder.withOwnerReferences(ResourceUtils.buildOwnerReference(this.entandoCustomResource));
        return metaBuilder.build();
    }
}
