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

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.extensions.DoneableIngress;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultIngressClient implements IngressClient {

    private final KubernetesClient client;

    public DefaultIngressClient(KubernetesClient client) {
        this.client = client;
    }

    public static String resolveMasterHostname(KubernetesClient client) {
        String host = client.settings().getMasterUrl().getHost();
        if ("127.0.0.1".equals(host)) {
            //This will only happen on single node installations. Generally it will return some ip/domain name that resolves to the master
            // Retrieve IP from node API and discard local IP
            Node masterNode = client.nodes().list().getItems().get(0);
            Optional<NodeAddress> masterNodeAddress = masterNode.getStatus().getAddresses().stream()
                    .filter(na -> na.getType().equalsIgnoreCase("InternalIP")).findFirst();
            host = masterNodeAddress.orElseThrow(() -> new RuntimeException("Impossible to retrieve node internal IP address"))
                    .getAddress();
        }
        return host;
    }

    @Override
    public Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations) {
        return client.extensions().ingresses().inNamespace(ingress.getMetadata().getNamespace())
                .withName(ingress.getMetadata().getName()).edit()
                .editSpec().editFirstRule().editHttp()
                .addNewPathLike(httpIngressPath)
                .endPath().endHttp().endRule().endSpec()
                .editMetadata().addToAnnotations(annotations).endMetadata()
                .done();
    }

    @Override
    public Ingress removeHttpPath(Ingress ingress, HTTPIngressPath path) {
        return client.extensions().ingresses().inNamespace(ingress.getMetadata().getNamespace())
                .withName(ingress.getMetadata().getName()).edit()
                .editSpec().editFirstRule().editHttp()
                .removeFromPaths(path)
                .endHttp()
                .endRule()
                .endSpec()
                .done();

    }

    @Override
    public String getMasterUrlHost() {
        return resolveMasterHostname(this.client);
    }

    @Override
    public Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(ingress);
    }

    @Override
    public DoneableIngress editIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).edit();
    }

    @Override
    public Ingress loadIngress(String namespace, String name) {
        return client.extensions().ingresses().inNamespace(namespace).withName(name).get();
    }
}
