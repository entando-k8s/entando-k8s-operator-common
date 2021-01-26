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

package org.entando.kubernetes.controller.support.client;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class InfrastructureConfig {

    public static final String DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY = "default-cluster-infrastructure-namespace";
    public static final String DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY = "default-cluster-infrastructure";
    public static final String ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY = "entandoK8SServiceInternalUrl";
    public static final String ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY = "entandoK8SServiceExternalUrl";
    public static final String ENTANDO_K8S_SERVICE_CLIENT_ID_KEY = "entandoK8SServiceClientId";
    private final ConfigMap infrastructureConfigMap;

    public InfrastructureConfig(ConfigMap infrastructureConfigMap) {
        this.infrastructureConfigMap = infrastructureConfigMap;
    }

    public ConfigMap getInfrastructureConfigMap() {
        return infrastructureConfigMap;
    }

    public String getK8SInternalServiceUrl() {
        return getInfrastructureConfigMap().getData().get(ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY);
    }

    public String getK8SExternalServiceUrl() {
        return getInfrastructureConfigMap().getData().get(ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY);
    }

    public String getK8sServiceClientId() {
        return getInfrastructureConfigMap().getData().get(ENTANDO_K8S_SERVICE_CLIENT_ID_KEY);
    }

    public static String connectionConfigMapNameFor(ResourceReference rr) {
        return connectionConfigMapNameFor(
                rr.getNamespace().orElseThrow(IllegalArgumentException::new),
                rr.getName());
    }

    public static String connectionConfigMapNameFor(EntandoClusterInfrastructure infrastructure) {
        return connectionConfigMapNameFor(infrastructure.getMetadata().getNamespace(), infrastructure.getMetadata().getName());
    }

    private static String connectionConfigMapNameFor(String namespace, String name) {
        return format("eci-%s-%s-connection-config", namespace,
                name);
    }

}
