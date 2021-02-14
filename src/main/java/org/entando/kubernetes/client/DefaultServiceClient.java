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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.function.Supplier;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultServiceClient implements ServiceClient {

    private final KubernetesClient client;

    public DefaultServiceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Endpoints createOrReplaceEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        //TODO remove the namespace overriding once we create delegate services from the correct context (the App)
        String namespace = ofNullable(endpoints.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        if (client.endpoints().inNamespace(namespace).withName(endpoints.getMetadata().getName()).get() != null) {
            client.endpoints().inNamespace(namespace).withName(endpoints.getMetadata().getName()).delete();
        }
        endpoints.getMetadata().setResourceVersion(null);
        return retry(() -> client.endpoints().inNamespace(namespace).create(endpoints));
    }

    private <T extends HasMetadata> T retry(Supplier<T> s) {
        KubernetesClientException exception=null;
        for (int i = 0; i < 10; i++) {
            try {
                return s.get();
            } catch (KubernetesClientException e) {
                //Waiting for K8S to delete it.
                exception=e;
            }
        }
        throw exception;

    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    @Override
    public Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service service) {
        String namespace = ofNullable(service.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        if (client.services().inNamespace(namespace).withName(service.getMetadata().getName()).get() != null) {
            client.services().inNamespace(namespace).withName(service.getMetadata().getName()).delete();
        }
        service.getMetadata().setResourceVersion(null);
        return retry(() -> client.services().inNamespace(namespace).create(service));
    }
}
