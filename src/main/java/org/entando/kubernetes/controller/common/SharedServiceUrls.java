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

package org.entando.kubernetes.controller.common;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class SharedServiceUrls {

    enum UrlType {EXTERNAL_KEYCLOAK_BASE_URL, EXTERNAL_K8S_SERVICE_URL}

    EntandoBaseCustomResource<?> resource;
    SecretClient client;

    public void putUrlIfAbsent(UrlType type, String url) {
    }

    public void overwriteUrl(UrlType type, String url) {
    }

    private String keyOf(UrlType urlType) {
        return format("%s-%s-%s", resource.getMetadata().getNamespace(), resource.getMetadata().getName(),
                urlType.name().toLowerCase().replace('_', '-'));
    }
}
