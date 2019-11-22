package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface PersistentVolumeClaimClient {

    PersistentVolumeClaim createPersistentVolumeClaim(EntandoCustomResource customResource,
            PersistentVolumeClaim persistentVolumeClaim);

    PersistentVolumeClaim loadPersistentVolumeClaim(EntandoCustomResource customResource, String name);

}
