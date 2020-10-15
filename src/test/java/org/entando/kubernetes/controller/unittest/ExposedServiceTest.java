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

package org.entando.kubernetes.controller.unittest;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.ExposedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ExposedServiceTest {

    @BeforeEach
    @AfterEach
    void clearProperties() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER.getJvmSystemProperty());
    }

    @Test
    void testUrlsOnMultiplePorts() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace("my-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withName("port1")
                .withPort(4545)
                .endPort()
                .addNewPort()
                .withName("port2")
                .withPort(4546)
                .endPort()
                .endSpec()
                .build(),
                new IngressBuilder()
                        .withNewSpec()
                        .addNewRule()
                        .withHost("test.apps.serv.run")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withNewBackend()
                        .withServiceName("my-service")
                        .withServicePort(new IntOrString(4545))
                        .endBackend()
                        .endPath()
                        .addNewPath()
                        .withPath("/path2")
                        .withNewBackend()
                        .withServiceName("my-service")
                        .withServicePort(new IntOrString(4546))
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThrows(IllegalStateException.class, () -> actual.getExternalBaseUrl());
        assertThat(actual.getExternalBaseUrlForPort("port1"), is("http://test.apps.serv.run/path1"));
        assertThat(actual.getExternalBaseUrlForPort("port2"), is("http://test.apps.serv.run/path2"));
        assertThrows(IllegalStateException.class, () -> actual.getInternalBaseUrl());
        assertThat(actual.getInternalBaseUrlForPort("port1"), is("http://my-service.my-namespace.svc.cluster.local:4545/path1"));
        assertThat(actual.getInternalBaseUrlForPort("port2"), is("http://my-service.my-namespace.svc.cluster.local:4546/path2"));
    }

    @Test
    void testUrlsOnSinglePorts() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace("my-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withName("port1")
                .withPort(4545)
                .endPort()
                .endSpec()
                .build(),
                new IngressBuilder()
                        .withNewSpec()
                        .addNewRule()
                        .withHost("test.apps.serv.run")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withNewBackend()
                        .withServiceName("my-service")
                        .withServicePort(new IntOrString(4545))
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(), is("http://test.apps.serv.run/path1"));
        assertThat(actual.getInternalBaseUrl(), is("http://my-service.my-namespace.svc.cluster.local:4545/path1"));
    }

    @Test
    void testUrlsOnDelegatingService() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace("another-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withName("port1")
                .withPort(4545)
                .endPort()
                .endSpec()
                .build(),
                new IngressBuilder()
                        .withNewSpec()
                        .addNewRule()
                        .withHost("test.apps.serv.run")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withNewBackend()
                        .withServiceName("thing-to-my-service")
                        .withServicePort(new IntOrString(4545))
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(), is("http://test.apps.serv.run/path1"));
        assertThat(actual.getInternalBaseUrl(), is("http://my-service.another-namespace.svc.cluster.local:4545/path1"));
    }

    @Test
    void testHttpsUrls() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace("my-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withName("port1")
                .withPort(4545)
                .endPort()
                .endSpec()
                .build(),
                new IngressBuilder()
                        .withNewSpec()
                        .addNewTl()
                        .addNewHost("test.apps.serv.run")
                        .endTl()
                        .addNewRule()
                        .withHost("test.apps.serv.run")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withNewBackend()
                        .withServiceName("my-service")
                        .withServicePort(new IntOrString(4545))
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(), is("https://test.apps.serv.run/path1"));
        assertThat(actual.getInternalBaseUrl(), is("http://my-service.my-namespace.svc.cluster.local:4545/path1"));
    }

    @Test
    void testExternalHttpsProvider() throws Exception {
        System.getProperties().put(EntandoOperatorConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER.getJvmSystemProperty(), "true");
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace("my-namespace")
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withName("port1")
                .withPort(4545)
                .endPort()
                .endSpec()
                .build(),
                new IngressBuilder()
                        .withNewSpec()
                        .addNewRule()
                        .withHost("test.apps.serv.run")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withNewBackend()
                        .withServiceName("my-service")
                        .withServicePort(new IntOrString(4545))
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(), is("https://test.apps.serv.run/path1"));
        assertThat(actual.getInternalBaseUrl(), is("http://my-service.my-namespace.svc.cluster.local:4545/path1"));
    }
}
