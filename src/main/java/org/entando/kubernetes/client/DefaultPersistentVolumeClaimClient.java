package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultPersistentVolumeClaimClient implements PersistentVolumeClaimClient {

    private final KubernetesClient client;

    public DefaultPersistentVolumeClaimClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public PersistentVolumeClaim createPersistentVolumeClaim(EntandoCustomResource peerInNamespace,
            PersistentVolumeClaim persistentVolumeClaim) {
        return client.persistentVolumeClaims().inNamespace(peerInNamespace.getMetadata().getNamespace())
                .create(persistentVolumeClaim);
    }

    @Override
    public PersistentVolumeClaim loadPersistentVolumeClaim(EntandoCustomResource peerInNamespace, String name) {
        return client.persistentVolumeClaims().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }
}
