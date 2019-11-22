package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class PersistentVolumentClaimClientDouble extends AbstractK8SClientDouble implements
        PersistentVolumeClaimClient {

    public PersistentVolumentClaimClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public PersistentVolumeClaim createPersistentVolumeClaim(EntandoCustomResource peerInNamespace,
            PersistentVolumeClaim persistentVolumeClaim) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putPersistentVolumeClaim(persistentVolumeClaim);
        return persistentVolumeClaim;
    }

    @Override
    public PersistentVolumeClaim loadPersistentVolumeClaim(EntandoCustomResource peerInNamespace, String name) {
        if (name == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getPersistentVolumeClaim(name);
    }
}
