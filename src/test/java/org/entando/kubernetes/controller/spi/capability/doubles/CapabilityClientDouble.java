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

package org.entando.kubernetes.controller.spi.capability.doubles;

import static java.util.Optional.ofNullable;

import com.google.common.base.Strings;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watch;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.capability.CapabilityClient;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.capability.CapabilityRequirementWatcher;
import org.entando.kubernetes.controller.support.client.doubles.AbstractK8SClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.NamespaceDouble;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.ExposedServerStatus;

public class CapabilityClientDouble extends AbstractK8SClientDouble implements CapabilityClient {

    public CapabilityClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name) {
        return ofNullable(getNamespace(namespace).getCustomResources(ProvidedCapability.class).get(name));
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(String namespace, Map<String, String> labels) {
        return byLabels(getNamespace(namespace).getCustomResources(ProvidedCapability.class).values(), labels);
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels) {
        return byLabels(getNamespaces().values().stream().flatMap(
                ns -> ns.getCustomResources(ProvidedCapability.class).values().stream()).collect(Collectors.toList()),
                labels);
    }

    private Optional<ProvidedCapability> byLabels(Collection<ProvidedCapability> capabilities, Map<String, String> labels) {
        return capabilities.stream().filter(
                capability -> labels.entrySet().stream().allMatch(
                        labelToMatch -> labelToMatch.getValue()
                                .equals(ofNullable(capability.getMetadata().getLabels()).orElse(Collections.emptyMap())
                                        .get(labelToMatch.getKey())))).findFirst();
    }

    @Override
    public Watch createAndWatchResource(ProvidedCapability capabilityRequirement, CapabilityRequirementWatcher watcher) {
        if (capabilityRequirement != null) {
            getNamespace(capabilityRequirement).getCustomResources(ProvidedCapability.class)
                    .put(capabilityRequirement.getMetadata().getName(), capabilityRequirement);
        }
        return () -> {
        };
    }

    @Override
    public String getNamespace() {
        return CONTROLLER_NAMESPACE;
    }

    @Override
    public CapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability) {
        AbstractServerStatus status = providedCapability.getStatus().findCurrentServerStatus()
                .orElseThrow(IllegalStateException::new);
        NamespaceDouble namespace = getNamespace(providedCapability);
        Ingress ingress = null;
        if (status instanceof ExposedServerStatus && !Strings.isNullOrEmpty(((ExposedServerStatus) status).getIngressName())) {
            ingress = namespace.getIngress(((ExposedServerStatus) status).getIngressName());
        }
        Service service = null;
        if (!Strings.isNullOrEmpty(status.getServiceName())) {
            service = namespace.getService(status.getServiceName());
        }
        return new CapabilityProvisioningResult(
                providedCapability,
                service,
                ingress,
                status.getAdminSecretName().map(s -> namespace.getSecret(s)).orElse(null)
        );
    }
}
