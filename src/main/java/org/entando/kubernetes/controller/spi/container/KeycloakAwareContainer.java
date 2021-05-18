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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public interface KeycloakAwareContainer extends DeployableContainer, HasWebContext, KeycloakPreference {

    KeycloakConnectionConfig getKeycloakConnectionConfig();

    KeycloakClientConfig getKeycloakClientConfig();

    default String getKeycloakRealmToUse() {
        return KeycloakName.ofTheRealm(this);
    }

    default String getPublicClientIdToUse() {
        return KeycloakName.ofThePublicClient(this);
    }

    default List<EnvVar> getKeycloakVariables() {
        KeycloakConnectionConfig keycloakDeployment = getKeycloakConnectionConfig();
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
        vars.add(new EnvVar("KEYCLOAK_REALM", getKeycloakRealmToUse(), null));
        vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", getPublicClientIdToUse(), null));
        vars.add(new EnvVar("KEYCLOAK_AUTH_URL", keycloakDeployment.getExternalBaseUrl(), null));
        String keycloakSecretName = KeycloakName.forTheClientSecret(getKeycloakClientConfig());
        vars.add(new EnvVar("KEYCLOAK_CLIENT_SECRET", null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar("KEYCLOAK_CLIENT_ID", null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }
}
