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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.controller.support.client.doubles.AbstractK8SClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.ClusterDouble;
import org.entando.kubernetes.controller.support.client.doubles.NamespaceDouble;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;

public class CapabilityClientDouble extends AbstractK8SClientDouble implements CapabilityClient {

    public CapabilityClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name) {
        if (namespace == null && name == null) {
            return Optional.empty();
        }
        return ofNullable(getNamespace(namespace).getCustomResources(ProvidedCapability.class).get(name));
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels) {
        return byLabels(getNamespaces().values().stream().flatMap(
                ns -> ns.getCustomResources(ProvidedCapability.class).values().stream()).collect(Collectors.toList()),
                labels);
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(String namespace, Map<String, String> labels) {
        if (namespace == null && labels == null) {
            return Optional.empty();
        }
        return byLabels(getNamespace(namespace).getCustomResources(ProvidedCapability.class).values(), labels);
    }

    @Override
    public ProvidedCapability createOrPatchCapability(ProvidedCapability providedCapability) {
        return getCluster().getResourceProcessor()
                .processResource(getNamespace(providedCapability).getCustomResources(providedCapability.getKind()), providedCapability);
    }

    @Override
    public ProvidedCapability waitForCapabilityCompletion(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException {
        if (capability != null) {
            CompletableFuture<ProvidedCapability> future = new CompletableFuture<>();
            getCluster().getResourceProcessor().watch(new Watcher<ProvidedCapability>() {
                @Override
                public void eventReceived(Action action, ProvidedCapability resource) {
                    if (resource.getStatus() != null && (resource.getStatus().getPhase() == EntandoDeploymentPhase.FAILED
                            || resource.getStatus().getPhase() == EntandoDeploymentPhase.SUCCESSFUL)) {
                        future.complete(resource);
                    }
                }

                @Override
                public void onClose(WatcherException cause) {

                }
            }, capability.getMetadata().getNamespace(), capability.getMetadata().getName());
            getCluster().getResourceProcessor()
                    .processResource(getNamespace(capability).getCustomResources(ProvidedCapability.class), capability);
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new TimeoutException(
                        format("Timed out waiting for ProvidedCapability. Please check the logs of the controller that processes the "
                                        + "capability '%s.capability.org'",
                                (capability.getSpec().getImplementation().map(i -> i.name() + ".").orElse("") + capability.getSpec()
                                        .getCapability().name()).toLowerCase(Locale.ROOT)));

            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private Optional<ProvidedCapability> byLabels(Collection<ProvidedCapability> capabilities, Map<String, String> labels) {
        return capabilities.stream().filter(
                capability -> labels.entrySet().stream().allMatch(
                        labelToMatch -> labelToMatch.getValue()
                                .equals(ofNullable(capability.getMetadata().getLabels()).orElse(Collections.emptyMap())
                                        .get(labelToMatch.getKey())))).findFirst();
    }

    @Override
    public String getNamespace() {
        return CONTROLLER_NAMESPACE;
    }

    @Override
    public SerializedCapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability) {
        if (providedCapability == null) {
            return null;
        }
        ServerStatus status = providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                .orElseThrow(IllegalStateException::new);
        NamespaceDouble namespace = getNamespace(providedCapability);
        Ingress ingress = status.getIngressName().map(namespace::getIngress).orElse(null);
        Service service = status.getServiceName().map(namespace::getService).orElse(null);
        return new SerializedCapabilityProvisioningResult(
                providedCapability,
                service,
                ingress,
                status.getAdminSecretName().map(namespace::getSecret).orElse(null)
        );
    }

    public void putCapability(ProvidedCapability foundCapability) {
        getCluster().getResourceProcessor()
                .processResource(getNamespace(foundCapability).getCustomResources(ProvidedCapability.class), foundCapability);

    }
}
