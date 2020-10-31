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

package org.entando.kubernetes.controller.common;

import static java.lang.String.format;

import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.KeycloakToUse;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.app.KeycloakAwareSpec;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class KeycloakName {

    public static final String CLIENT_SECRET_KEY = "clientSecret";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String DEFAULT_KEYCLOAK_NAME_KEY = "default-keycloak-name";
    public static final String DEFAULT_KEYCLOAK_NAMESPACE_KEY = "default-keycloak-namespace";

    public static String forTheClientSecret(KeycloakClientConfig keycloakConfig) {
        return keycloakConfig.getClientId() + "-secret";
    }

    public static String forTheConnectionConfigMap(EntandoKeycloakServer keycloakServer) {
        return forTheConnectionConfigMap(keycloakServer.getMetadata().getNamespace(), keycloakServer.getMetadata().getName());
    }

    public static String forTheConnectionConfigMap(ResourceReference resourceReference) {
        return forTheConnectionConfigMap(resourceReference.getNamespace(), resourceReference.getName());
    }

    public static String forTheAdminSecret(ResourceReference resourceReference) {
        return forTheAdminSecret(resourceReference.getNamespace(), resourceReference.getName());
    }

    public static String forTheAdminSecret(EntandoKeycloakServer entandoKeycloakServer) {
        return forTheAdminSecret(entandoKeycloakServer.getMetadata().getNamespace(), entandoKeycloakServer.getMetadata().getName());
    }

    private static String forTheAdminSecret(String namespace, String name) {
        if (namespace == null && name == null) {
            return "keycloak-admin-secret"; //for the ability to define this upfront without an Entando controller Keycloak
        }
        return format("keycloak-%s-%s-admin-secret", namespace, name);
    }

    private static String forTheConnectionConfigMap(String namespace, String name) {
        if (namespace == null && name == null) {
            return "keycloak-connection-config"; //for the ability to define this upfront without an Entando controller Keycloak
        }
        return format("keycloak-%s-%s-connection-config", namespace, name);
    }

    public static String ofTheRealm(KeycloakAwareSpec keycloakAwareSpec) {
        return keycloakAwareSpec.getKeycloakToUse()
                .map(KeycloakToUse::getRealm)
                .map(Optional::get)
                .orElse(KubeUtils.ENTANDO_DEFAULT_KEYCLOAK_REALM);
    }

    public static String ofThePublicClient(KeycloakAwareSpec keycloakAwareSpec) {
        return keycloakAwareSpec.getKeycloakToUse()
                .map(KeycloakToUse::getPublicClientId)
                .map(Optional::get)
                .orElse(KubeUtils.PUBLIC_CLIENT_ID);
    }
}
