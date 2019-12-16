package org.entando.kubernetes.controller.spi;

import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;

public interface KeycloakAware extends DeployableContainer, HasWebContext {

    @Deprecated
    default KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return getKeycloakConnectionConfig();
    }

    KeycloakConnectionConfig getKeycloakConnectionConfig();

    KeycloakClientConfig getKeycloakClientConfig();

}
