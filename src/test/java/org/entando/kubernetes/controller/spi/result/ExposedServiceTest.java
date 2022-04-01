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

package org.entando.kubernetes.controller.spi.result;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.test.common.ControllerTestHelper;
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
        System.getProperties()
                .remove(EntandoOperatorConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER.getJvmSystemProperty());
    }

    @Test
    void testUrlsOnMultiplePorts() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace(ControllerTestHelper.MY_NAMESPACE)
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
                        .withHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4545).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .addNewPath()
                        .withPath("/path2")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4546).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThrows(IllegalStateException.class, () -> actual.getExternalBaseUrl());
        assertThat(actual.getExternalBaseUrlForPort("port1"),
                is("http://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path1"));
        assertThat(actual.getExternalBaseUrlForPort("port2"),
                is("http://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path2"));
        assertThrows(IllegalStateException.class, () -> actual.getInternalBaseUrl());
        assertThat(actual.getInternalBaseUrlForPort("port1"),
                is("http://my-service." + ControllerTestHelper.MY_NAMESPACE + ".svc.cluster.local:4545/path1"));
        assertThat(actual.getInternalBaseUrlForPort("port2"),
                is("http://my-service." + ControllerTestHelper.MY_NAMESPACE + ".svc.cluster.local:4546/path2"));
    }

    @Test
    void testUrlsOnSinglePorts() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace(ControllerTestHelper.MY_NAMESPACE)
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
                        .withHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4545).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(),
                is("http://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path1"));
        assertThat(actual.getInternalBaseUrl(),
                is("http://my-service." + ControllerTestHelper.MY_NAMESPACE + ".svc.cluster.local:4545/path1"));
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
                        .withHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("thing-to-my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4545).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(),
                is("http://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path1"));
        assertThat(actual.getInternalBaseUrl(), is("http://my-service.another-namespace.svc.cluster.local:4545/path1"));
    }

    @Test
    void testHttpsUrls() throws Exception {
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace(ControllerTestHelper.MY_NAMESPACE)
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
                        .addNewHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .endTl()
                        .addNewRule()
                        .withHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4545).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(),
                is("https://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path1"));
        assertThat(actual.getInternalBaseUrl(),
                is("http://my-service." + ControllerTestHelper.MY_NAMESPACE + ".svc.cluster.local:4545/path1"));
    }

    @Test
    void testExternalHttpsProvider() throws Exception {
        System.getProperties()
                .put(EntandoOperatorConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER.getJvmSystemProperty(),
                        "true");
        ExposedService actual = new ExposedService(new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withNamespace(ControllerTestHelper.MY_NAMESPACE)
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
                        .withHost("test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "")
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/path1")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName("my-service")
                        .withPort(new ServiceBackendPortBuilder().withNumber(4545).build())
                        .endService()
                        .endBackend()
                        .endPath()
                        .endHttp()
                        .endRule()
                        .endSpec()
                        .build());
        assertThat(actual.getExternalBaseUrl(),
                is("https://test." + EntandoOperatorTestConfig.mustGetDefaultRoutingSuffix() + "/path1"));
        assertThat(actual.getInternalBaseUrl(),
                is("http://my-service." + ControllerTestHelper.MY_NAMESPACE + ".svc.cluster.local:4545/path1"));
    }
}
