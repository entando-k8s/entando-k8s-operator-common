package org.entando.kubernetes.controller.common.examples;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.integrationtest.KeycloakClientIT;
import org.entando.kubernetes.controller.spi.IngressingContainer;

public class MinimalKeycloakContainer implements IngressingContainer {

    @Override
    public String determineImageToUse() {
        return "entando/entando-keycloak:6.0.0-SNAPSHOT";
    }

    @Override
    public String getNameQualifier() {
        return "server";
    }

    @Override
    public int getPort() {
        return 8080;
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("DB_VENDOR", "h2", null));
        vars.add(new EnvVar("KEYCLOAK_USER", "test-admin", null));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", KeycloakClientIT.KCP, null));
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }
}
