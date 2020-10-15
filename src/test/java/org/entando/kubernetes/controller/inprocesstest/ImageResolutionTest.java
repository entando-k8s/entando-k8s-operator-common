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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.entando.kubernetes.controller.EntandoImageResolver;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment")})
class ImageResolutionTest {

    private ConcurrentHashMap<Object, Object> storedProps = new ConcurrentHashMap<>();

    @BeforeEach
    void backupSystemProperties() {
        storedProps = new ConcurrentHashMap<>(System.getProperties());
    }

    @AfterEach
    void restoreSystemProperties() {
        Stream.of(EntandoOperatorConfigProperty.values()).forEach(p -> {
            System.getProperties().remove(p.getJvmSystemProperty());
        });
        System.getProperties().putAll(storedProps);
    }

    @Test
    void testResolutionFromDefaultProperties() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromDefaultPropertiesThatAreOverridden() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromDefaultPropertiesWhenThereIsAConfigMap() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I have a configMap
        //when I resolve an image
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromDefaultPropertiesWhenThereIsAConfigMapButItIsOverridden() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "overridden.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(),
                "overridden-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "overridden");
        //And I have a configMap
        //when I resolve an image
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("overridden.io/overridden-entando/test-image:overridden"));
    }

    @Test
    void testVersionResolution() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I have a configMap
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        //when I resolve an image
        Optional<String> version = new EntandoImageResolver(imageVersionsConfigMap).determineLatestVersionOf("test-image");
        //then it reflects the overriding property values
        assertThat(version.get(), is("6.1.4"));
    }

    @Test
    void testResolutionIgnoredForNonEntandoImages() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("test.io/not-entando/test-image:1", Optional.empty());
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/not-entando/test-image:1"));
    }
}
