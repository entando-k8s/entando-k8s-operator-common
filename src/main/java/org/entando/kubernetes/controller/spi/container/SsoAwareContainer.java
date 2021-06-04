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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public interface SsoAwareContainer extends DeployableContainer, HasWebContext, KeycloakPreference {

    @Override
    SsoConnectionInfo getSsoConnectionInfo();

    SsoClientConfig getSsoClientConfig();

    default String getRealmToUse() {
        return KeycloakName.ofTheRealm(this);
    }

    @Override
    default String getPublicClientIdToUse() {
        return KeycloakName.ofThePublicClient(this);
    }

    default List<EnvVar> getSsoVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
        vars.add(new EnvVar("KEYCLOAK_REALM", getRealmToUse(), null));
        vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", getPublicClientIdToUse(), null));
        ofNullable(getSsoConnectionInfo()).ifPresent(ssoConnectionInfo ->
                vars.add(new EnvVar("KEYCLOAK_AUTH_URL", ssoConnectionInfo.getExternalBaseUrl(), null)));
        String keycloakSecretName = KeycloakName.forTheClientSecret(getSsoClientConfig());
        vars.add(new EnvVar("KEYCLOAK_CLIENT_SECRET", null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar("KEYCLOAK_CLIENT_ID", null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }
}
