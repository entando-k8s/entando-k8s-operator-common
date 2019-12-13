package org.entando.kubernetes.controller.spi;

import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;

public interface KeycloakAware extends DeployableContainer, HasWebContext {

    KeycloakConnectionConfig getKeycloakDeploymentResult();

    KeycloakClientConfig getKeycloakClientConfig();

}
