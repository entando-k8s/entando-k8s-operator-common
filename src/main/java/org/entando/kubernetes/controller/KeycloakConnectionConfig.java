package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public interface KeycloakConnectionConfig {

    static String decodeSecretValue(Secret adminSecret, String key) {
        String value = ofNullable(adminSecret.getData()).map(stringStringMap -> stringStringMap.get(key)).orElse(null);
        if (value == null) {
            //If not yet reloaded
            return adminSecret.getStringData().get(key);
        } else {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    Secret getAdminSecret();

    String getBaseUrl();

    default String getUsername() {
        return decodeSecretValue(getAdminSecret(), KubeUtils.USERNAME_KEY);
    }

    default String getPassword() {
        return decodeSecretValue(getAdminSecret(), KubeUtils.PASSSWORD_KEY);
    }

}
