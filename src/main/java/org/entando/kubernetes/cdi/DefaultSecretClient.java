package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultSecretClient implements SecretClient {

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultSecretClient(DefaultKubernetesClient client) {
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

}
