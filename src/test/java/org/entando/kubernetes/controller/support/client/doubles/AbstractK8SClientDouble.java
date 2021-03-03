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

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractK8SClientDouble {

    public static final String CONTROLLER_NAMESPACE = "controller-namespace";
    private final ConcurrentHashMap<String, NamespaceDouble> namespaces;

    public AbstractK8SClientDouble() {
        this.namespaces = new ConcurrentHashMap<>();
    }

    public AbstractK8SClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces) {
        this.namespaces = namespaces;
        getNamespace(CONTROLLER_NAMESPACE);
    }

    protected NamespaceDouble getNamespace(HasMetadata customResource) {
        return getNamespace(customResource.getMetadata().getNamespace());
    }

    protected synchronized NamespaceDouble getNamespace(String namespace) {
        return this.namespaces.computeIfAbsent(namespace, NamespaceDouble::new);
    }

    public ConcurrentHashMap<String, NamespaceDouble> getNamespaces() {
        return namespaces;
    }
}
