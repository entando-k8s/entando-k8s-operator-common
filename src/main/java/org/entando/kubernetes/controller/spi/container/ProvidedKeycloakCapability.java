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

package org.entando.kubernetes.controller.spi.container;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.ExternallyProvidedService;

public class ProvidedKeycloakCapability implements KeycloakConnectionConfig {

    private final CapabilityProvisioningResult capabilityResult;
    private final ExposedService exposedService;

    public ProvidedKeycloakCapability(CapabilityProvisioningResult capabilityResult) {
        super();
        this.capabilityResult = capabilityResult;
        this.exposedService = new ExposedService(capabilityResult.getService(), capabilityResult.getIngress().orElse(null));
    }

    @Override
    public Secret getAdminSecret() {
        return capabilityResult.getAdminSecret().orElse(null);
    }

    @Override
    public String determineBaseUrl() {
        if (EntandoOperatorSpiConfig.forceExternalAccessToKeycloak()
                || capabilityResult.getProvidedCapability().getSpec().getProvisioningStrategy().orElse(
                CapabilityProvisioningStrategy.DEPLOY_DIRECTLY) == CapabilityProvisioningStrategy.USE_EXTERNAL) {
            return getExternalBaseUrl();
        } else {
            return getInternalBaseUrl().orElse(getExternalBaseUrl());
        }
    }

    @Override
    public String getExternalBaseUrl() {
        return ofNullable(capabilityResult.getProvidedCapability().getSpec().getCapabilityParameters().get("frontEndUrl"))
                .or(this::buildExternalServiceUrl)
                .orElse(exposedService.getExternalBaseUrl());
    }

    private Optional<String> buildExternalServiceUrl() {
        final Optional<ExternallyProvidedService> providedService = capabilityResult.getProvidedCapability().getSpec()
                .getExternallyProvisionedService();
        if (providedService.isPresent()) {
            final ExternallyProvidedService service = providedService.get();
            return Optional.of("https://" + service.getHost() + service.getPort().map(integer -> ":" + integer).orElse("") + "/auth");
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getInternalBaseUrl() {
        return ofNullable(exposedService.getInternalBaseUrl());
    }
}
