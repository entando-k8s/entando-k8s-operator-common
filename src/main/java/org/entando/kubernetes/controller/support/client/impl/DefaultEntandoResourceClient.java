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

package org.entando.kubernetes.controller.support.client.impl;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.spi.client.impl.DefaultKubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultEntandoResourceClient extends DefaultKubernetesClientForControllers implements
        EntandoResourceClient, PatchableClient {

    public DefaultEntandoResourceClient(KubernetesClient client) {
        super(client);
    }

    @Override
    public ConfigMap loadDockerImageInfoConfigMap() {
        return client.configMaps().inNamespace(EntandoOperatorConfig.getEntandoDockerImageInfoNamespace().orElse(client.getNamespace()))
                .withName(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()).fromServer().get();
    }

    @Override
    public ExposedService loadExposedService(EntandoCustomResource resource) {
        return new ExposedService(
                loadService(resource, NameUtils.standardServiceName(resource)),
                loadIngress(resource, NameUtils.standardIngressName(resource)));
    }

    private Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    private Ingress loadIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    protected Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }
}
