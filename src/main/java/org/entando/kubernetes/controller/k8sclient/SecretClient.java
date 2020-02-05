package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface SecretClient {

    /**
     * Creates secrets in the same namespace as the controllers for subsequent use. Overwrites the secret if it already exists.
     *
     * @param secret the secret to be created.
     */
    void overwriteControllerSecret(Secret secret);

    /**
     * Creates secrets in the same namespace as the Entando Custom Resource specified, but only if the secret doesn't already exist. It
     * assumes that subsequent deployments will reuse the existing secret.
     *
     * @param peerInNamespace EntandoCustomResource that determines the namespace that the secret is to be created in.
     * @param secret the secret to be created.
     */
    //TODO we need to revisit this. There are different scenarios that require different behaviour here, e.g.
    // generated DB secrets, Keycloak generated secrets, TLS secrets
    void createSecretIfAbsent(EntandoCustomResource peerInNamespace, Secret secret);

    Secret loadSecret(EntandoCustomResource peerInNamespace, String secretName);

    ConfigMap loadControllerConfigMap(String configMapName);
}
