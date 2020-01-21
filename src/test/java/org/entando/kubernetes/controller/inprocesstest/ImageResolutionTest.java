package org.entando.kubernetes.controller.inprocesstest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.entando.kubernetes.controller.EntandoImageResolver;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("in-process")
public class ImageResolutionTest {

    private Map<String, String> storedProps = new ConcurrentHashMap<>();

    @BeforeEach
    public void backupSystemProperties() {
        Stream.of(EntandoOperatorConfigProperty.values()).forEach(p -> {
            if (System.getProperties().containsKey(p.getJvmSystemProperty())) {
                storedProps.put(p.getJvmSystemProperty(), (String) System.getProperties().remove(p.getJvmSystemProperty()));
            }
        });

    }

    @AfterEach
    public void restoreSystemProperties() {
        System.getProperties().putAll(storedProps);
    }

    @Test
    public void testResolutionFromDefaultProperties() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    public void testResolutionFromDefaultPropertiesThatAreOverridden() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_OVERRIDE.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    public void testResolutionFromDefaultPropertiesWhenThereIsAConfigMap() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "default");
        //And I have a configMap
        //when I resolve an image
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"docker-registry\":\"test.io\",\"image-namespace\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    public void testResolutionFromDefaultPropertiesWhenThereIsAConfigMapButItIsOverridden() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "overridden.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_OVERRIDE.getJvmSystemProperty(),
                "overridden-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "overridden");
        //And I have a configMap
        //when I resolve an image
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"docker-registry\":\"test.io\",\"image-namespace\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image", Optional.empty());
        //then it reflects the overriding property values
        assertThat(imageUri, is("overridden.io/overridden-entando/test-image:overridden"));
    }

    @Test
    public void testVersionResolution() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "default");
        //And I have a configMap
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"docker-registry\":\"test.io\",\"image-namespace\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        //when I resolve an image
        Optional<String> version = new EntandoImageResolver(imageVersionsConfigMap).determineLatestVersionOf("test-image");
        //then it reflects the overriding property values
        assertThat(version.get(), is("6.1.4"));
    }

    @Test
    public void testResolutionIgnoredForNonEntandoImages() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("test.io/not-entando/test-image:1", Optional.empty());
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/not-entando/test-image:1"));
    }
}