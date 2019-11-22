package org.entando.kubernetes.controller.spi;

import org.entando.kubernetes.controller.KeycloakConnectionConfig;

public interface PublicIngressingDeployable<T extends ServiceResult> extends IngressingDeployable<T> {

    String getPublicKeycloakClientId();

    KeycloakConnectionConfig getKeycloakDeploymentResult();
}
