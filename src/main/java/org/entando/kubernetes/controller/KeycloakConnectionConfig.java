package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public interface KeycloakConnectionConfig extends SecretBasedCredentials {


    @Override
    default Secret getSecret(){
        return getAdminSecret();
    }

    Secret getAdminSecret();


    String getBaseUrl();

}
