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

package org.entando.kubernetes.controller.spi.client;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Collection;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public interface CustomResourceClient {

    String ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME = "entando-operator-config";
    String CRD_KIND_LABEL_NAME = "entando.org/kind";

    void prepareConfig();

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    void updatePhase(EntandoCustomResource customResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    default EntandoCustomResource resolveCustomResourceToProcess(Collection<Class<? extends EntandoCustomResource>> supportedTypes) {
        String resourceName = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME);
        String resourceNamespace = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE);
        String kind = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND);
        return load(supportedTypes.stream().filter(c -> c.getSimpleName().equals(kind)).findAny().orElseThrow(() ->
                        new IllegalArgumentException(format("The resourceKind %s was not found in the list of supported types", kind))),
                resourceNamespace, resourceName);

    }

    private String resolveProperty(EntandoOperatorSpiConfigProperty name) {
        return EntandoOperatorConfigBase.lookupProperty(name)
                .orElseThrow(() -> new IllegalStateException(
                        format("No %s specified. Please set either the Environment Variable %s or the System Property %s", name
                                .getCamelCaseName(), name.name(), name.getJvmSystemProperty())));
    }

    SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name);

    HasMetadata loadStandardResource(String kind, String namespace, String name);
}
