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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.entando.kubernetes.model.app.EntandoApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultServiceClientTest extends AbstractK8SIntegrationTest {

    private final EntandoApp entandoApp = newTestEntandoApp();

    @BeforeEach
    void deleteServices() {
        deleteAll(getFabric8Client().services());
    }

    @Test
    void shouldCreateOrReplaceService() {
        //Given I have an existing service with the annotation "test: 123"
        final Service firstService = getSimpleK8SClient().services().createOrReplaceService(entandoApp,
                new ServiceBuilder()
                        .withNewMetadata()
                        .withNamespace(entandoApp.getMetadata().getNamespace())
                        .withName("my-service")
                        .addToAnnotations("test", "123")
                        .endMetadata()
                        .withNewSpec()
                        .withExternalName("google.com")
                        .addNewPort()
                        .withPort(80)
                        .endPort()
                        .endSpec()
                        .build());
        //When I attempt to findOrCreate a service with the same name but with the annotation "test: 234"
        firstService.getMetadata().getAnnotations().put("test", "234");
        getSimpleK8SClient().services().createOrReplaceService(entandoApp, firstService);
        //Then it has reflects the new annotation
        final Service second = getSimpleK8SClient().services().loadService(entandoApp, "my-service");
        assertThat(second.getMetadata().getAnnotations().get("test"), is("234"));
    }

    @Test
    void shouldCreateOrReplaceEndpoints() {
        //Given I have an existing service with the annotation "test: 123"
        final Endpoints firstEndpoints = getSimpleK8SClient().services().createOrReplaceEndpoints(entandoApp,
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withNamespace(entandoApp.getMetadata().getNamespace())
                        .withName("my-service")
                        .addToAnnotations("test", "123")
                        .endMetadata()
                        .addNewSubset()
                        .addNewAddress().withIp("172.17.0.123").endAddress()
                        .addNewPort("my-port", 80, null)
                        .endSubset()
                        .build());
        //When I attempt to findOrCreate a service with the same name but with the annotation "test: 234"
        firstEndpoints.getSubsets().get(0).getPorts().get(0).setPort(81);
        final Endpoints secondEndpoints = getSimpleK8SClient().services().createOrReplaceEndpoints(entandoApp, firstEndpoints);
        //Then it has reflects the new annotation
        assertThat(secondEndpoints.getSubsets().get(0).getPorts().get(0).getPort(), is(81));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{entandoApp.getMetadata().getNamespace()};
    }
}
