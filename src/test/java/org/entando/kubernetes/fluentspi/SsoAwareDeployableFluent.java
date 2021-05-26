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

package org.entando.kubernetes.fluentspi;

import static java.util.Optional.ofNullable;

import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.KeycloakToUse;

public class SsoAwareDeployableFluent<N extends SsoAwareDeployableFluent<N>> extends IngressingDeployableFluent<N> implements
        PublicIngressingDeployable<DefaultExposedDeploymentResult> {

    private KeycloakToUse preferredKeycloakToUse;
    private SsoConnectionInfo ssoConnectionInfo;

    @Override
    public SsoConnectionInfo getSsoConnectionInfo() {
        return this.ssoConnectionInfo;
    }

    public <C extends DeployableContainerFluent<C>> C withContainer(C container) {
        ofNullable(ssoConnectionInfo).ifPresent(((SsoAwareContainerFluent<?>) container)::withSsoConnectionConfig);
        return super.withContainer(container);

    }

    public N withSsoConnectionConfig(SsoConnectionInfo ssoConnectionInfo) {
        this.ssoConnectionInfo = ssoConnectionInfo;
        getContainers().stream().filter(SsoAwareContainerFluent.class::isInstance).map(SsoAwareContainerFluent.class::cast)
                .forEach(c -> c.withSsoConnectionConfig(ssoConnectionInfo));
        return thisAsN();
    }

    @Override
    public Optional<KeycloakToUse> getPreferredKeycloakToUse() {
        return Optional.ofNullable(this.preferredKeycloakToUse);
    }

    public N withPreferredKeycloakToUse(KeycloakToUse preferredKeycloakToUse) {
        this.preferredKeycloakToUse = preferredKeycloakToUse;
        return thisAsN();
    }

}
