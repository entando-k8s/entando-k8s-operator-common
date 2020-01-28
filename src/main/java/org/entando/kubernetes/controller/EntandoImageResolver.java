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
        return determineVersion(imagename);
    }

    public String determineImageUri(String imagename, Optional<String> version) {
        if (isEntandoImage(imagename)) {
            String imageNameSegment = imagename.substring("entando/".length());
            return format("%s/%s/%s:%s",
                    determineDockerRegistry(imageNameSegment),
                    determineOrganization(imageNameSegment),
                    imageNameSegment,
                    version.orElse(determineVersion(imageNameSegment).orElse("latest")));
        } else {
            return imagename;
        }
    }

    private String determineDockerRegistry(String imagename) {
        return new PropertyResolution(this.imageVersionsConfigMap, imagename)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE)
                .withConfigMapKey("registry")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK)
                .withDefaultValue("docker.io").resolvePropertyValue();
    }

    private String determineOrganization(String imagename) {
        return new PropertyResolution(this.imageVersionsConfigMap, imagename)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE)
                .withConfigMapKey("organization")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_DEFAULT)
                .withDefaultValue("entando").resolvePropertyValue();
    }

    private Optional<String> determineVersion(String imagenameSegment) {
        return Optional.ofNullable(new PropertyResolution(this.imageVersionsConfigMap, imagenameSegment)
                .withOverridingPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE)
                .withConfigMapKey("version")
                .withDefaultPropertyName(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK)
                .withDefaultValue(null).resolvePropertyValue());
    }

}
