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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import java.util.Map;
import java.util.Optional;

public class DefaultCapabilityClient implements SimpleCapabilityClient {

    KubernetesClient client;

    @Override
    public Optional<ConfigMap> configMapByName(String namespace, String name) {
        return Optional.ofNullable(client.configMaps().inNamespace(namespace).withName(name).fromServer().get());
    }

    @Override
    public Optional<ConfigMap> configMapByLabels(Map<String, String> labels) {
        return client.configMaps().withLabels(labels).list().getItems().stream().findFirst();
    }

    @Override
    public Optional<ConfigMap> configMapByLabels(String namespace, Map<String, String> labels) {
        return client.configMaps().inNamespace(namespace).withLabels(labels).list().getItems().stream().findFirst();
    }

    @Override
    public Watch createAndWatchResource(CapabilityRequirement capabilityRequirement, CapabilityRequirementWatcher watcher) {
        client.customResources(CapabilityRequirement.class).inNamespace(capabilityRequirement.getMetadata().getNamespace())
                .create(capabilityRequirement);
        return client.customResources(CapabilityRequirement.class)
                .inNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName()).watch(watcher);
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }
}
