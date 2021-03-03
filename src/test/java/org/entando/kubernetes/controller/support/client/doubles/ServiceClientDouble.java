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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceClientDouble extends AbstractK8SClientDouble implements ServiceClient {

    public ServiceClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service service) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putService(service.getMetadata().getName(), service);
        return service;
    }

    @Override
    public Endpoints createOrReplaceEndpoints(EntandoCustomResource peerInNamespace,
            Endpoints endpoints) {
        getNamespace(peerInNamespace).putEndpoints(endpoints);
        return getNamespace(peerInNamespace).getEndpoints(endpoints.getMetadata().getName());
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getService(name);
    }
}
