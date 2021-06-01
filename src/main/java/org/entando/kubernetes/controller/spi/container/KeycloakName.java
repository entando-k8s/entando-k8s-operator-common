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

import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.model.common.KeycloakToUse;

public class KeycloakName {

    public static final String CLIENT_SECRET_KEY = "clientSecret";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String DEFAULT_KEYCLOAK_NAME_KEY = "default-keycloak-name";
    public static final String DEFAULT_KEYCLOAK_NAMESPACE_KEY = "default-keycloak-namespace";
    public static final String DEFAULT_KEYCLOAK_ADMIN_SECRET = "keycloak-admin-secret";
    public static final String DEFAULT_KEYCLOAK_CONNECTION_CONFIG = "keycloak-connection-config";
    public static final String PUBLIC_CLIENT_ID = "entando-web";
    public static final String ENTANDO_DEFAULT_KEYCLOAK_REALM = "entando";

    private KeycloakName() {
        //because this is a utility class
    }

    public static String forTheClientSecret(SsoClientConfig keycloakConfig) {
        return keycloakConfig.getClientId() + "-secret";
    }

    public static String ofTheRealm(KeycloakPreference keycloakPreference) {
        return keycloakPreference.getPreferredKeycloakToUse()
                .flatMap(KeycloakToUse::getRealm)
                .or(() -> ofNullable(keycloakPreference.getSsoConnectionInfo()).flatMap(SsoConnectionInfo::getDefaultRealm))
                .orElse(ENTANDO_DEFAULT_KEYCLOAK_REALM);
    }

    public static String ofThePublicClient(KeycloakPreference keycloakAwareSpec) {
        return keycloakAwareSpec.getPreferredKeycloakToUse()
                .map(keycloakToUse -> keycloakToUse.getPublicClientId().orElse(PUBLIC_CLIENT_ID))
                .orElse(PUBLIC_CLIENT_ID);
    }
}
