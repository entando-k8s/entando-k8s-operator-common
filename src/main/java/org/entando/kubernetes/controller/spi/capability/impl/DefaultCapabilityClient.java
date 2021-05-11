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

package org.entando.kubernetes.controller.spi.capability.impl;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.capability.CapabilityRequirementWatcher;
import org.entando.kubernetes.controller.spi.capability.CapabilityClient;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.ExposedServerStatus;

public class DefaultCapabilityClient implements CapabilityClient {

    private final KubernetesClient client;

    public DefaultCapabilityClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name) {
        return Optional
                .ofNullable(client.customResources(ProvidedCapability.class).inNamespace(namespace).withName(name).fromServer().get());
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels) {
        return client.customResources(ProvidedCapability.class).inAnyNamespace().withLabels(labels).list().getItems().stream().findFirst();
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(String namespace, Map<String, String> labels) {
        return client.customResources(ProvidedCapability.class).inNamespace(namespace).withLabels(labels).list().getItems().stream()
                .findFirst();
    }

    @Override
    public Watch createAndWatchResource(ProvidedCapability capabilityRequirement, CapabilityRequirementWatcher watcher) {
        client.customResources(ProvidedCapability.class).inNamespace(capabilityRequirement.getMetadata().getNamespace())
                .create(capabilityRequirement);
        return client.customResources(ProvidedCapability.class)
                .inNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName()).watch(watcher);
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public CapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability) {
        final AbstractServerStatus serverStatus = providedCapability.getStatus().findCurrentServerStatus()
                .orElseThrow(IllegalStateException::new);
        Service service = client.services().inNamespace(providedCapability.getMetadata().getNamespace())
                .withName(serverStatus.getServiceName()).get();
        Secret adminSecret = serverStatus.getAdminSecretName()
                .map(s -> client.secrets().inNamespace(providedCapability.getMetadata().getNamespace()).withName(s).get())
                .orElse(null);
        Ingress ingress = null;
        if (serverStatus instanceof ExposedServerStatus) {
            ingress = client.extensions().ingresses().inNamespace(providedCapability.getMetadata().getNamespace())
                    .withName(((ExposedServerStatus) serverStatus).getIngressName()).get();
        }
        return new CapabilityProvisioningResult(providedCapability, service, ingress, adminSecret);
    }
}
