package org.entando.kubernetes.controller;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.Optional;

public class EntandoImageResolver {

    private final ConfigMap imageVersionsConfigMap;

    public EntandoImageResolver(ConfigMap imageVersionsConfigMap) {
        this.imageVersionsConfigMap = imageVersionsConfigMap;
    }

    private static boolean isEntandoImage(String imagename) {
        //Only images in the entando namespace that don't already have versions
        return imagename.startsWith("entando/") && !imagename.contains(":");
    }

    public Optional<String> determineLatestVersionOf(String imagename) {
        return determineImageVersion(imagename);
    }

    public String determineImageUri(String imagename, Optional<String> version) {
        if (isEntandoImage(imagename)) {
            String imageNameSegment = imagename.substring("entando/".length());
            return format("%s/%s/%s:%s",
                    determineDockerRegistry(imageNameSegment),
                    determineDockerNamespace(imageNameSegment),
                    imageNameSegment,
                    version.orElse(determineImageVersion(imageNameSegment).orElse("latest")));
        } else {
            return imagename;
        }
    }

    private String determineDockerRegistry(String imagename) {
        return new PropertyResolution(this.imageVersionsConfigMap, imagename)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE)
                .withConfigMapKey("docker-registry")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_DEFAULT)
                .withDefaultValue("docker.io").resolvePropertyValue();
    }

    private String determineDockerNamespace(String imagename) {
        return new PropertyResolution(this.imageVersionsConfigMap, imagename)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_OVERRIDE)
                .withConfigMapKey("image-namespace")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT)
                .withDefaultValue("entando").resolvePropertyValue();
    }

    private Optional<String> determineImageVersion(String imagename) {
        return Optional.ofNullable(new PropertyResolution(this.imageVersionsConfigMap, imagename)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE)
                .withConfigMapKey("version")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_DEFAULT)
                .withDefaultValue(null).resolvePropertyValue());
    }

}
