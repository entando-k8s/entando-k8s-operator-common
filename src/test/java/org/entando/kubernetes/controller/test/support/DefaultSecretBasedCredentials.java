package org.entando.kubernetes.controller.test.support;

import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.controller.SecretBasedCredentials;

public class DefaultSecretBasedCredentials  implements SecretBasedCredentials {
    private final Secret secret;

    public DefaultSecretBasedCredentials(Secret secret) {
        this.secret = secret;
    }

    @Override
    public Secret getSecret() {
        return secret;
    }
}
