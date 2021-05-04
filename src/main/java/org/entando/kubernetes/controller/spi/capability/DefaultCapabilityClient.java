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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.model.capability.ProvidedCapability;

public class DefaultCapabilityClient implements SimpleCapabilityClient {

    KubernetesClient client;

    @Override
    public Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name) {
        return Optional
                .ofNullable(client.customResources(ProvidedCapability.class).inNamespace(namespace).withName(name).fromServer().get());
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels) {
        return client.customResources(ProvidedCapability.class).withLabels(labels).list().getItems().stream().findFirst();
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
}
