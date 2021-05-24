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

package org.entando.kubernetes.controller.support.capability;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.support.client.WaitingClient;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;

public class DefaultCapabilityClient implements CapabilityClient, WaitingClient {

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
    public ProvidedCapability createAndWaitForCapability(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException {
        client.customResources(ProvidedCapability.class).inNamespace(capability.getMetadata().getNamespace())
                .create(capability);
        try {
            return interruptionSafe(() -> client.customResources(ProvidedCapability.class)
                    .inNamespace(capability.getMetadata().getNamespace())
                    .withName(capability.getMetadata().getName())
                    .waitUntilCondition(providedCapability -> providedCapability.getStatus() != null
                                    && (providedCapability.getStatus().getPhase() == EntandoDeploymentPhase.FAILED
                                    || providedCapability.getStatus().getPhase() == EntandoDeploymentPhase.SUCCESSFUL), 10,
                            TimeUnit.MINUTES));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("matching condition not found")) {
                //NB!! keep an eye on this. It is an annoying implementation detail that we need to sync with
                throw new TimeoutException(
                        format("Timed out waiting for ProvidedCapability. Please check the logs of the controller that processes the "
                                        + "capability '%s.capability.org'",
                                (capability.getSpec().getImplementation().map(i -> i.name() + ".").orElse("") + capability.getSpec()
                                        .getCapability().name()).toLowerCase(Locale.ROOT)));
            }
            throw e;
        }
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public SerializedCapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability) {
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
        return new SerializedCapabilityProvisioningResult(providedCapability, service, ingress, adminSecret);
    }
}
