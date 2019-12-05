package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
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
    public ConfigMap loadControllerConfigMap(String configMapName) {
        return client.configMaps().inNamespace(client.getNamespace()).withName(configMapName).get();
    }
}
