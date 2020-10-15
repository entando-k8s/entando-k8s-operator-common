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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultSecretClient implements SecretClient {

    private final KubernetesClient client;

    public DefaultSecretClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void overwriteControllerSecret(Secret secret) {
        try {
            secret.getMetadata().setNamespace(client.getNamespace());
            client.secrets().createOrReplace(secret);
        } catch (KubernetesClientException e) {
            KubernetesExceptionProcessor.verifyDuplicateExceptionOnCreate(client.getNamespace(), secret, e);
        }

    }

    @Override
    public void createSecretIfAbsent(EntandoCustomResource peerInNamespace, Secret secret) {
        try {
            client.secrets().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(secret);
        } catch (KubernetesClientException e) {
            KubernetesExceptionProcessor.squashDuplicateExceptionOnCreate(peerInNamespace, secret, e);
        }

    }

    @Override
    public Secret loadSecret(EntandoCustomResource peerInNamespace, String secretName) {
        try {
            return client.secrets().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(secretName).get();
        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor.processExceptionOnLoad(peerInNamespace, e, "Secret", secretName);
        }
    }

    @Override
    public Secret loadControllerSecret(String secretName) {
        try {
            return client.secrets().inNamespace(client.getNamespace()).withName(secretName).get();
        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor.processExceptionOnLoad(e, "Secret", client.getNamespace(), secretName);
        }
    }

    @Override
    public ConfigMap loadControllerConfigMap(String configMapName) {
        return client.configMaps().inNamespace(EntandoOperatorConfig.getOperatorConfigMapNamespace().orElse(client.getNamespace()))
                .withName(configMapName).get();
    }
}
