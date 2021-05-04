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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultServiceClient implements ServiceClient {

    private final KubernetesClient client;

    public DefaultServiceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Endpoints createOrReplaceEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        //TODO remove the namespace overriding once we create delegate services from the correct context (the App)
        return createOrReplace(peerInNamespace, endpoints, client.endpoints());
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    @Override
    public Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service endpoints) {
        return createOrReplace(peerInNamespace, endpoints, client.services());
    }

    private <T extends HasMetadata, L extends KubernetesResourceList<T>, R extends Resource<T>> T createOrReplace(
            EntandoCustomResource peerInNamespace, T endpoints,
            MixedOperation<T, L, R> oper) {
        String namespace = ofNullable(endpoints.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        if (oper.inNamespace(namespace).withName(endpoints.getMetadata().getName()).get() != null) {
            oper.inNamespace(namespace).withName(endpoints.getMetadata().getName()).delete();
        }
        endpoints.getMetadata().setResourceVersion(null);
        for (int i = 0; i < 10; i++) {
            try {
                return oper.inNamespace(namespace).create(endpoints);
            } catch (KubernetesClientException e) {
                //Waiting for K8S to delete it.
            }
        }
        throw new IllegalStateException("Could not create.");
    }
}
