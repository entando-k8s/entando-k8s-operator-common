package org.entando.kubernetes.controller;

import io.fabric8.kubernetes.api.model.Secret;

public interface KeycloakConnectionConfig extends SecretBasedCredentials {

    @Override
    default Secret getSecret() {
        return getAdminSecret();
    }

    Secret getAdminSecret();

    String getBaseUrl();

}
