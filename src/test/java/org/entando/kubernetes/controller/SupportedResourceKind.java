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

package org.entando.kubernetes.controller;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.DoneablePersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.api.model.extensions.DoneableIngress;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;

public enum SupportedResourceKind {
    SERVICE {
        @SuppressWarnings("unchecked")
        public MixedOperation<
                Service,
                ServiceList,
                DoneableService,
                ServiceResource<Service, DoneableService>
                > getOperation(KubernetesClient client) {
            return client.services();
        }
    },
    DEPLOYMENT {
        @SuppressWarnings("unchecked")
        public MixedOperation<
                Deployment,
                DeploymentList,
                DoneableDeployment,
                RollableScalableResource<Deployment, DoneableDeployment>
                > getOperation(KubernetesClient client) {
            return client.apps().deployments();
        }
    },
    POD {
        @SuppressWarnings("unchecked")
        public MixedOperation<
                Pod,
                PodList,
                DoneablePod,
                PodResource<Pod, DoneablePod>
                > getOperation(KubernetesClient client) {
            return client.pods();
        }
    },
    INGRESS {
        @SuppressWarnings("unchecked")
        public MixedOperation<
                Ingress,
                IngressList,
                DoneableIngress,
                Resource<Ingress, DoneableIngress>
                > getOperation(KubernetesClient client) {
            return client.extensions().ingresses();
        }
    },
    PERSISTENT_VOLUME_CLAIM {
        @SuppressWarnings("unchecked")
        public MixedOperation<
                PersistentVolumeClaim,
                PersistentVolumeClaimList,
                DoneablePersistentVolumeClaim,
                Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim>
                > getOperation(KubernetesClient client) {
            return client.persistentVolumeClaims();
        }
    };

    SupportedResourceKind() {

    }

    public <
            T extends HasMetadata,
            L extends KubernetesResourceList<T>,
            D extends Doneable<T>,
            R extends Resource<T, D>
            > MixedOperation<T, L, D, R> getOperation(KubernetesClient client) {
        return null;
    }

    public static Optional<SupportedResourceKind> resolveFromKind(String kind) {
        try {
            return Optional.of(valueOf(NameUtils.upperSnakeCaseOf(kind)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
