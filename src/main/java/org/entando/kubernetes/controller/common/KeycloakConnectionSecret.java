package org.entando.kubernetes.controller.common;

import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;

public class KeycloakConnectionSecret implements KeycloakConnectionConfig {

    private final Secret adminSecret;

    public KeycloakConnectionSecret(Secret adminSecret) {
        this.adminSecret = adminSecret;
    }

    @Override
    public Secret getAdminSecret() {
        return adminSecret;
    }

    @Override
    public String getBaseUrl() {
        return decodeSecretValue(KubeUtils.URL_KEY);
    }
}
