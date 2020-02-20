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

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.KeycloakClientCreator;

public interface KeycloakAware extends DeployableContainer, HasWebContext {

    KeycloakConnectionConfig getKeycloakConnectionConfig();

    KeycloakClientConfig getKeycloakClientConfig();

    default void addKeycloakVariables(List<EnvVar> vars) {
        KeycloakConnectionConfig keycloakDeployment = getKeycloakConnectionConfig();
        vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
        vars.add(new EnvVar("KEYCLOAK_REALM", KubeUtils.ENTANDO_KEYCLOAK_REALM, null));
        vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", KubeUtils.PUBLIC_CLIENT_ID, null));
        vars.add(new EnvVar("KEYCLOAK_AUTH_URL", keycloakDeployment.getBaseUrl(), null));
        String keycloakSecretName = KeycloakClientCreator.keycloakClientSecret(getKeycloakClientConfig());
        vars.add(new EnvVar("KEYCLOAK_CLIENT_SECRET", null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar("KEYCLOAK_CLIENT_ID", null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_ID_KEY)));

    }
}
