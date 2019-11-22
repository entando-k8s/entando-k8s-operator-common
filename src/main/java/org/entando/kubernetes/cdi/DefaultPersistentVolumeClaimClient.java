package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultPersistentVolumeClaimClient implements PersistentVolumeClaimClient {

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultPersistentVolumeClaimClient(DefaultKubernetesClient client) {
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
