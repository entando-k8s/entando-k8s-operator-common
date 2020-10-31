/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

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
    void createSecretIfAbsent(EntandoBaseCustomResource<?> peerInNamespace, Secret secret);

    Secret loadSecret(EntandoCustomResource peerInNamespace, String secretName);

    Secret loadControllerSecret(String secretName);

    ConfigMap loadControllerConfigMap(String configMapName);

    void createConfigMapIfAbsent(EntandoBaseCustomResource<?> peerInNamespace, ConfigMap configMap);

    ConfigMap loadConfigMap(EntandoBaseCustomResource<?> peerInNamespace, String name);
}
