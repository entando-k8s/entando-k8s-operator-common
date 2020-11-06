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

package org.entando.kubernetes.controller.creators;

import static java.util.Collections.singletonMap;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.ConfigurableStorageCalculator;
import org.entando.kubernetes.controller.common.StorageCalculator;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.spi.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class PersistentVolumeClaimCreator<T extends EntandoDeploymentSpec> extends AbstractK8SResourceCreator<T> {

    private List<PersistentVolumeClaim> persistentVolumeClaims;

    public PersistentVolumeClaimCreator(EntandoBaseCustomResource<T> entandoCustomResource) {
        super(entandoCustomResource);
    }

    public boolean needsPersistentVolumeClaaims(Deployable<?, ?> deployable) {
        return deployable.getContainers().stream()
                .anyMatch(PersistentVolumeAware.class::isInstance);
    }

    public void createPersistentVolumeClaimsFor(PersistentVolumeClaimClient k8sClient, Deployable<?, ?> deployable) {
        this.persistentVolumeClaims = deployable.getContainers().stream()
                .filter(PersistentVolumeAware.class::isInstance)
                .map(PersistentVolumeAware.class::cast)
                .map(deployableContainer -> k8sClient
                        .createPersistentVolumeClaimIfAbsent(entandoCustomResource,
                                newPersistentVolumeClaim(deployable, deployableContainer)))
                .collect(Collectors.toList());

    }

    public List<PersistentVolumeClaimStatus> reloadPersistentVolumeClaims(PersistentVolumeClaimClient k8sClient) {
        return Optional.ofNullable(persistentVolumeClaims).orElse(Collections.emptyList()).stream()
                .map(persistentVolumeClaim -> k8sClient.loadPersistentVolumeClaim(entandoCustomResource,
                        persistentVolumeClaim.getMetadata().getName()).getStatus())
                .collect(Collectors.toList());
    }

    private PersistentVolumeClaim newPersistentVolumeClaim(Deployable<?, ?> deployable, PersistentVolumeAware container) {
        StorageCalculator resourceCalculator = buildStorageCalculator(container);
        return new PersistentVolumeClaimBuilder()
                .withMetadata(fromCustomResource(!EntandoOperatorConfig.disablePvcGarbageCollection(),
                        resolveName(container.getNameQualifier(), "-pvc"),
                        deployable.getNameQualifier()))
                .withNewSpec().withAccessModes("ReadWriteOnce")
                .withNewResources()
                .withRequests(singletonMap("storage", new Quantity(resourceCalculator.getStorageRequest())))
                .withLimits(singletonMap("storage", new Quantity(resourceCalculator.getStorageLimit())))
                .endResources().endSpec()
                .build();
    }

    private StorageCalculator buildStorageCalculator(PersistentVolumeAware deployableContainer) {
        return deployableContainer instanceof ConfigurableResourceContainer
                ? new ConfigurableStorageCalculator((ConfigurableResourceContainer) deployableContainer)
                : new StorageCalculator(deployableContainer);

    }

}
