package org.entando.kubernetes.controller.common.example;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;

//TODO replace with KeycloakConnectionSecret
public class KeycloakDeploymentResult extends ServiceDeploymentResult implements KeycloakConnectionConfig {

    private final KeycloakServer keycloakServer;
    private final Secret adminSecret;

    public KeycloakDeploymentResult(Service service, Ingress ingress, KeycloakServer keycloakServer, Secret adminSecret) {
        super(service, ingress);
        this.keycloakServer = keycloakServer;
        this.adminSecret = adminSecret;
    }

    @Override
    public Secret getAdminSecret() {
        return adminSecret;
    }

    @Override
    public String getBaseUrl() {
        return getExternalBaseUrl();
    }

    public KeycloakServer getKeycloakServer() {
        return keycloakServer;
    }
}

