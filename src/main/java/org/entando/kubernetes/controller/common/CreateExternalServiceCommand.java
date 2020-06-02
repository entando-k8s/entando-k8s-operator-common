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

package org.entando.kubernetes.controller.common;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.entando.kubernetes.controller.FluentTernary;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbServerStatus;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class CreateExternalServiceCommand {

    private static final int TCP4_NUMBER_OF_BYTES = 4;
    private static final int TCP6_NUMBER_OF_SEGMENTS = 8;
    private final EntandoDatabaseService externalDatabase;
    private final DbServerStatus status = new DbServerStatus();

    public CreateExternalServiceCommand(EntandoDatabaseService externalDatabase) {
        this.externalDatabase = externalDatabase;
        status.setQualifier("external-db");
    }

    public DbServerStatus getStatus() {
        return status;
    }

    public ExternalDatabaseDeployment execute(SimpleK8SClient k8sClient) {
        Service service = k8sClient.services().createOrReplaceService(externalDatabase, newExternalService());
        Endpoints endpoints = maybeCreateEndpoints(k8sClient);
        this.status.setServiceStatus(service.getStatus());
        return new ExternalDatabaseDeployment(service, endpoints, externalDatabase);
    }

    public Endpoints maybeCreateEndpoints(SimpleK8SClient k8sClient) {
        Endpoints endpoints = null;
        if (isIpAddress()) {
            endpoints = newEndpoints();
            k8sClient.services().createOrReplaceEndpoints(externalDatabase, endpoints);
        }
        return endpoints;
    }

    private Endpoints newEndpoints() {
        return new EndpointsBuilder()
                //Needs to match the service name exactly
                .withMetadata(fromCustomResource("-service", true))
                .addNewSubset()
                .addNewAddress()
                .withIp(externalDatabase.getSpec().getHost())
                .endAddress()
                .addNewPort()
                .withPort(getPort())
                .endPort()
                .endSubset()
                .build();
    }

    private Integer getPort() {
        return externalDatabase.getSpec().getPort().orElse(DbmsDockerVendorStrategy.forVendor(externalDatabase.getSpec().getDbms()).getPort());
    }

    private Service newExternalService() {
        return new ServiceBuilder()
                .withMetadata(fromCustomResource("-service", true))
                .withNewSpec()
                .withExternalName(FluentTernary.useNull(String.class).when(isIpAddress()).orElse(externalDatabase.getSpec().getHost()))
                .withType(FluentTernary.use("ClusterIP").when(isIpAddress()).orElse("ExternalName"))
                .addNewPort()
                .withNewTargetPort(
                        getPort())
                .withPort(getPort())
                .endPort()
                .endSpec()
                .build();
    }

    private boolean isIpAddress() {
        String host = externalDatabase.getSpec().getHost();
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

    private ObjectMeta fromCustomResource(String suffix, boolean ownedByCustomResource) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(externalDatabase.getMetadata().getName() + suffix)
                .withNamespace(externalDatabase.getMetadata().getNamespace())
                .addToLabels(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, externalDatabase.getKind())
                .addToLabels(externalDatabase.getKind(), externalDatabase.getMetadata().getName());
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(new OwnerReferenceBuilder()
                    .withApiVersion(externalDatabase.getApiVersion())
                    .withBlockOwnerDeletion(true)
                    .withController(true)
                    .withKind(externalDatabase.getKind())
                    .withName(externalDatabase.getMetadata().getName())
                    .withUid(externalDatabase.getMetadata().getUid()).build());
        }
        return metaBuilder.build();
    }
}
