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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.entando.kubernetes.model.app.EntandoApp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultSecretClientTest extends AbstractK8SIntegrationTest {

    private final EntandoApp entandoApp = newTestEntandoApp();

    @Test
    void shouldCreateSecretIfAbsent() {
        //Given I have an existing Secret  with the data field "test: 123"
        final Secret firstSecret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .withName("my-local-secret")
                .endMetadata()
                .addToData("test", Base64.getEncoder().encodeToString("123".getBytes(StandardCharsets.UTF_8)))
                .build();
        getSimpleK8SClient().secrets().createSecretIfAbsent(entandoApp, firstSecret);
        //When I attempt to create a Secret  with the same name but with data filed "test: 234"
        firstSecret.getData().put("test", Base64.getEncoder().encodeToString("234".getBytes(StandardCharsets.UTF_8)));
        getSimpleK8SClient().secrets().createSecretIfAbsent(entandoApp, firstSecret);
        //Then it has the original Secret remains in tact
        final Secret secondSecret = getSimpleK8SClient().secrets()
                .loadSecret(entandoApp, "my-local-secret");
        assertThat(secondSecret.getData().get("test"), is(Base64.getEncoder().encodeToString("123".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldOverwriteControllerSecret() {
        //Given I have an existing Secret  with the data field "test: 123"
        final Secret firstSecret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .withName("my-controller-secret")
                .endMetadata()
                .addToData("test", Base64.getEncoder().encodeToString("123".getBytes(StandardCharsets.UTF_8)))
                .build();
        getSimpleK8SClient().secrets().overwriteControllerSecret(firstSecret);
        //When I attempt to create a Secret  with the same name but with data filed "test: 234"
        firstSecret.getData().put("test", Base64.getEncoder().encodeToString("234".getBytes(StandardCharsets.UTF_8)));
        getSimpleK8SClient().secrets().overwriteControllerSecret(firstSecret);
        //Then it the new value reflects
        final Secret secondSecret = getSimpleK8SClient().secrets()
                .loadControllerSecret("my-controller-secret");
        assertThat(secondSecret.getData().get("test"), is(Base64.getEncoder().encodeToString("234".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldOverwriteControllerConfigMap() {
        //Given I have an existing ConfigMap in the Controller namespace with data field test=123
        getSimpleK8SClient().secrets().overwriteControllerConfigMap(new ConfigMapBuilder()
                .withNewMetadata()
                .withName("test-map-can-delete-this")
                .endMetadata()
                .addToData("test", "123")
                .build());
        //When I overwrite it with a new map with data field test=234
        getSimpleK8SClient().secrets().overwriteControllerConfigMap(new ConfigMapBuilder()
                .withNewMetadata()
                .withName("test-map-can-delete-this")
                .endMetadata()
                .addToData("test", "234")
                .build());
        //Then the new value reflects
        assertThat(getSimpleK8SClient().secrets().loadControllerConfigMap("test-map-can-delete-this").getData().get("test"), is("234"));
    }

    @Test
    void shouldCreateConfigMapIfAbsent() {
        //Given I have an existing ConfigMap  with the data field "test: 123"
        final ConfigMap firstConfigMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .withName("my-local-configmap")
                .endMetadata()
                .addToData("test", "123")
                .build();
        getSimpleK8SClient().secrets().createConfigMapIfAbsent(entandoApp, firstConfigMap);
        //When I attempt to findOrCreate a ConfigMap  with the same name but with the data field "test: 234"
        firstConfigMap.getData().put("test", "123");
        getSimpleK8SClient().secrets().createConfigMapIfAbsent(entandoApp, firstConfigMap);
        //Then it has the original ConfigMap remains in tact
        final ConfigMap secondConfigMap = getSimpleK8SClient().secrets()
                .loadConfigMap(entandoApp, "my-local-configmap");
        assertThat(secondConfigMap.getData().get("test"), is("123"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{entandoApp.getMetadata().getNamespace()};
    }

}
