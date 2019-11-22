package org.entando.kubernetes.controller;

import java.util.Optional;

public final class EntandoImageResolver {

    private EntandoImageResolver() {
    }

    public static String determineImageUri(String imagename, Optional<String> version) {
        if (isEntandoImage(imagename)) {
            String imageNameSegment = imagename.substring("entando/".length());
            return EntandoOperatorConfig.getEntandoDockerRegistry() + "/" + EntandoOperatorConfig.getEntandoImageNamespace() + "/"
                    + imageNameSegment + ":"
                    + version.orElse(EntandoOperatorConfig.getEntandoImageVersion());
        } else {
            return imagename;
        }
    }

    private static boolean isEntandoImage(String imagename) {
        return imagename.startsWith("entando/");
    }
}
