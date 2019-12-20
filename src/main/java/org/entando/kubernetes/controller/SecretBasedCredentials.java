package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public interface SecretBasedCredentials {

    default String decodeSecretValue(String key) {
        String value = ofNullable(getSecret().getData()).map(stringStringMap -> stringStringMap.get(key)).orElse(null);
        if (value == null) {
            //If not yet reloaded
            return getSecret().getStringData().get(key);
        } else {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }

    }

    default String getUsername() {
        return decodeSecretValue(KubeUtils.USERNAME_KEY);
    }

    default String getPassword() {
        return decodeSecretValue(KubeUtils.PASSSWORD_KEY);
    }

    Secret getSecret();
}
