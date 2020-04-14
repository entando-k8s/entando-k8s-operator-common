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

package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tag("in-process")
@EnableRuleMigrationSupport
public class DefaultClientTest {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private DefaultSimpleK8SClient defaultSimpleK8SClient;

    @BeforeEach
    public void setup() {
        defaultSimpleK8SClient = new DefaultSimpleK8SClient(server.getClient());
    }

    @Test
    public void shouldRemovePathFromIngress() {

        EntandoPlugin plugin = new EntandoPluginBuilder()
                .withNewMetadata()
                .withNamespace("my-namespace")
                .endMetadata()
                .build();
        Ingress myIngress = getTestIngress();
        myIngress.getMetadata().setNamespace("my-namespace");
        Ingress deployedIngress = this.defaultSimpleK8SClient.ingresses().createIngress(plugin, myIngress);

        Assertions.assertTrue(() -> deployedIngress.getSpec().getRules().get(0).getHttp().getPaths().size() == 2);

        HTTPIngressPath ingressPath = myIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        Ingress cleanedIngress = this.defaultSimpleK8SClient.ingresses().removeHttpPath(deployedIngress, ingressPath);

        Assertions.assertFalse(() ->
                cleanedIngress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                        .anyMatch(p -> p.getPath().equals(ingressPath.getPath())
                                && p.getBackend().getServicePort().equals(ingressPath.getBackend().getServicePort())
                                && p.getBackend().getServiceName().equals(ingressPath.getBackend().getServiceName())));

        Assertions.assertTrue(() -> cleanedIngress.getSpec().getRules().get(0).getHttp()
                .getPaths().size() == 1);
    }

    private Ingress getTestIngress() {
        return new IngressBuilder()
                .withNewMetadata()
                .withName("my-ingress")
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost("my-host-local")
                .withNewHttp()
                .addNewPath()
                .withPath("/path1")
                .withNewBackend()
                .withServiceName("path1-plugin")
                .withServicePort(new IntOrString(8081))
                .endBackend()
                .endPath()
                .addNewPath()
                .withPath("/path2")
                .withNewBackend()
                .withServiceName("path2-plugin")
                .withServicePort(new IntOrString("8081"))
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }

}
