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

package org.entando.kubernetes.controller.spi.capability;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import java.io.Serializable;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ServiceResult;
import org.entando.kubernetes.model.ResourceReference;

public class ProvidedCapability implements Serializable {

    public static final String CAPABILITY_LABEL_NAME = "capability";
    public static final String IMPLEMENTATION_LABEL_NAME = "implementation";
    public static final String CAPABILITY_PROVISION_SCOPE_LABEL_NAME = "capabilityProvisionScope";
    public static final String SERVICE_NAME_KEY = "serviceName";
    public static final String SERVICE_NAMESPACE_KEY = "serviceNamespace";
    public static final String INGRESS_NAME_KEY = "ingressName";
    public static final String INGRESS_NAMESPACE_KEY = "ingressNamespace";
    public static final String ADMIN_SECRET_NAME_KEY = "adminSecretName";
    public static final String ADMIN_SECRET_NAMESPACE_KEY = "adminSecretNamespace";
    //<Put this in the labels of the ConfigMap>
    private StandardCapability capability;
    private StandardCapabilityImplementation implementation;
    private CapabilityScope capabilityProvisionScope;
    //</Put this in the labels of the ConfigMap>
    private ResourceReference serviceReference;
    private ResourceReference ingressReference;
    private ResourceReference adminSecretReference;

    public ProvidedCapability(StandardCapabilityImplementation implementation, CapabilityScope capabilityProvisionScope) {
        this.capability = implementation.getCapability();
        this.implementation = implementation;
        this.capabilityProvisionScope = capabilityProvisionScope;
    }

    public ProvidedCapability(ConfigMap configMap) {
        this.capability = StandardCapability.valueOf(NameUtils.upperSnakeCaseOf(configMap.getMetadata().getLabels().get(
                CAPABILITY_LABEL_NAME)));
        this.implementation = StandardCapabilityImplementation
                .valueOf(NameUtils.upperSnakeCaseOf(configMap.getMetadata().getLabels().get(IMPLEMENTATION_LABEL_NAME)));
        this.capabilityProvisionScope = CapabilityScope
                .valueOf(NameUtils.upperSnakeCaseOf(configMap.getMetadata().getLabels().get(CAPABILITY_PROVISION_SCOPE_LABEL_NAME)));
        this.serviceReference = new ResourceReference(configMap.getData().get(SERVICE_NAMESPACE_KEY), configMap.getData().get(
                SERVICE_NAME_KEY));
        if (configMap.getData().containsKey(INGRESS_NAME_KEY)) {
            this.ingressReference = new ResourceReference(configMap.getData().get(INGRESS_NAMESPACE_KEY),
                    configMap.getData().get(INGRESS_NAME_KEY));
        }
        this.adminSecretReference = new ResourceReference(configMap.getData().get(ADMIN_SECRET_NAMESPACE_KEY),
                configMap.getData().get(ADMIN_SECRET_NAME_KEY));
    }

    public ConfigMap toConfigMap(HasMetadata capabilityCustomResource, ServiceResult deploymentResult, Secret adminSecret) {
        ConfigMapBuilder configMapBuilder = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(capabilityCustomResource.getMetadata().getName())
                .withNamespace(capabilityCustomResource.getMetadata().getNamespace())
                .addToLabels(CAPABILITY_LABEL_NAME, capability.getCamelCaseName())
                .addToLabels(IMPLEMENTATION_LABEL_NAME, implementation.getCamelCaseName())
                .addToLabels(CAPABILITY_PROVISION_SCOPE_LABEL_NAME, capabilityProvisionScope.getCamelCaseName())
                .endMetadata()
                .addToData(SERVICE_NAME_KEY, deploymentResult.getService().getMetadata().getName())
                .addToData(SERVICE_NAMESPACE_KEY, deploymentResult.getService().getMetadata().getNamespace())
                .addToData(ADMIN_SECRET_NAME_KEY, adminSecret.getMetadata().getName())
                .addToData(ADMIN_SECRET_NAMESPACE_KEY, adminSecret.getMetadata().getNamespace());
        if (deploymentResult instanceof ExposedDeploymentResult) {
            final ExposedDeploymentResult<?> exposedResult = (ExposedDeploymentResult<?>) deploymentResult;
            configMapBuilder = configMapBuilder
                    .addToData(INGRESS_NAME_KEY, exposedResult.getIngress().getMetadata().getName())
                    .addToData(INGRESS_NAMESPACE_KEY, exposedResult.getIngress().getMetadata().getNamespace());
        }
        return configMapBuilder.build();
    }

    public StandardCapability getCapability() {
        return capability;
    }

    public StandardCapabilityImplementation getImplementation() {
        return implementation;
    }

    public CapabilityScope getCapabilityProvisionScope() {
        return capabilityProvisionScope;
    }

    public ResourceReference getServiceReference() {
        return serviceReference;
    }

    public Optional<ResourceReference> getIngressReference() {
        return Optional.ofNullable(ingressReference);
    }

    public ResourceReference getAdminSecretReference() {
        return adminSecretReference;
    }
}
