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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.controller.spi.container.SsoAwareContainer;
import org.entando.kubernetes.controller.spi.container.SsoClientConfig;
import org.entando.kubernetes.controller.spi.container.SsoConnectionInfo;
import org.entando.kubernetes.model.common.KeycloakToUse;

public class SsoAwareContainerFluent<N extends SsoAwareContainerFluent<N>> extends IngressingContainerFluent<N> implements
        SsoAwareContainer {

    private SsoConnectionInfo ssoConnectionInfo;
    private SsoClientConfig ssoClientConfig;

    @Override
    public Optional<KeycloakToUse> getPreferredKeycloakToUse() {
        return Optional.empty();
    }

    @Override
    public SsoConnectionInfo getSsoConnectionInfo() {
        return this.ssoConnectionInfo;
    }

    public N withSsoConnectionConfig(SsoConnectionInfo ssoConnectionInfo) {
        this.ssoConnectionInfo = ssoConnectionInfo;
        return thisAsN();
    }

    @Override
    public SsoClientConfig getSsoClientConfig() {
        return this.ssoClientConfig;
    }

    public N withSoClientConfig(SsoClientConfig ssoClientConfig) {
        this.ssoClientConfig = ssoClientConfig;
        return thisAsN();
    }

    @Override
    public List<EnvVar> getSsoVariables() {
        List<EnvVar> vars = SsoAwareContainer.super.getSsoVariables();
        ofNullable(getSsoConnectionInfo()).ifPresent(ssoConnectionInfo -> {
            final String realmToUse = KeycloakName.ofTheRealm(this);
            vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name(),
                    ssoConnectionInfo.getExternalBaseUrl() + "/realms/" + realmToUse,
                    null));
        });

        String keycloakSecretName = KeycloakName.forTheClientSecret(getSsoClientConfig());
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }
}
