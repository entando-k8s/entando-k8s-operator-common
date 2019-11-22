package org.entando.kubernetes.controller.common;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class InfrastructureConfig {

    private final Secret infrastructureSecret;

    public InfrastructureConfig(Secret infrastructureSecret) {
        this.infrastructureSecret = infrastructureSecret;
    }

    public static String decodeSecretValue(Secret secret, String key) {
        String value = ofNullable(secret.getData()).map(stringStringMap -> stringStringMap.get(key)).orElse(null);
        if (value == null) {
            //If not yet reloaded
            return secret.getStringData().get(key);
        } else {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    public Secret getInfrastructureSecret() {
        return infrastructureSecret;
    }

    public String getK8SInternalServiceUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceInternalUrl");
    }

    public String getUserManagementInternalUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "userManagementInternalUrl");
    }

    public String getK8SExternalServiceUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceExternalUrl");
    }

    public String getUserManagementExternalUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "userManagementExternalUrl");
    }

    public String getK8sServiceClientId() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceClientId");
    }
}
