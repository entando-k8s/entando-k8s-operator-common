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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class DefaultPersistentVolumeClaimClient implements PersistentVolumeClaimClient {

    private final KubernetesClient client;

    public DefaultPersistentVolumeClaimClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public <S extends EntandoDeploymentSpec> PersistentVolumeClaim createPersistentVolumeClaimIfAbsent(
            EntandoBaseCustomResource<S> peerInNamespace,
            PersistentVolumeClaim persistentVolumeClaim) {
        PersistentVolumeClaim existing = client.persistentVolumeClaims()
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(persistentVolumeClaim.getMetadata().getName()).get();
        if (existing == null) {
            return client.persistentVolumeClaims()
                    .inNamespace(peerInNamespace.getMetadata().getNamespace()).create(persistentVolumeClaim);
        }
        return existing;
    }

    @Override
    public <S extends EntandoDeploymentSpec> PersistentVolumeClaim loadPersistentVolumeClaim(EntandoBaseCustomResource<S> peerInNamespace,
            String name) {
        return client.persistentVolumeClaims().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }
}
