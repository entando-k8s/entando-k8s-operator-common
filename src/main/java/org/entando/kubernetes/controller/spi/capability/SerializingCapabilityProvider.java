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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.CommandStream;
import org.entando.kubernetes.controller.spi.command.DeserializationHelper;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.command.SupportedCommand;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ExposedServerStatus;

public class SerializingCapabilityProvider implements CapabilityProvider {

    final KubernetesClientForControllers kubernetesClient;
    final CommandStream commandStream;

    public SerializingCapabilityProvider(KubernetesClientForControllers kubernetesClient, CommandStream commandStream) {
        this.kubernetesClient = kubernetesClient;
        this.commandStream = commandStream;
    }

    @Override
    public CapabilityProvisioningResult provideCapability(EntandoCustomResource forResource, CapabilityRequirement capabilityRequirement,
            int timeoutSeconds) throws TimeoutException {
        final String provisioningResult = commandStream.process(SupportedCommand.PROVIDE_CAPABILITY,
                SerializationHelper.serialize(new SerializableCapabilityForResource(forResource, capabilityRequirement)), timeoutSeconds);
        return DeserializationHelper.deserialize(kubernetesClient, provisioningResult);
    }

    @Override
    public CapabilityProvisioningResult loadProvisioningResult(ProvidedCapability providedCapability) {
        final AbstractServerStatus serverStatus = providedCapability.getStatus().findCurrentServerStatus()
                .orElseThrow(IllegalStateException::new);
        Service service = ofNullable(serverStatus.getServiceName()).map(s -> (Service) kubernetesClient
                .loadStandardResource("Service", providedCapability.getMetadata().getNamespace(), s)).orElse(null);
        Secret adminSecret = serverStatus.getAdminSecretName()
                .map(s -> (Secret) kubernetesClient.loadStandardResource("Secret", providedCapability.getMetadata().getNamespace(), s))
                .orElse(null);
        Ingress ingress = null;
        if (serverStatus instanceof ExposedServerStatus) {
            final ExposedServerStatus exposedServerStatus = (ExposedServerStatus) serverStatus;
            ingress = ofNullable(exposedServerStatus.getIngressName())
                    .map(i -> (Ingress) kubernetesClient
                            .loadStandardResource("Ingress", providedCapability.getMetadata().getNamespace(), i))
                    .orElse(null);
        }
        return new SerializedCapabilityProvisioningResult(providedCapability, service, ingress, adminSecret);
    }
}
