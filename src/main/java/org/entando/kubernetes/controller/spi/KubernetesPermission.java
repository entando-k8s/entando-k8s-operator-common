package org.entando.kubernetes.controller.spi;

import java.util.Arrays;
import java.util.List;

public class KubernetesPermission {

    private final String apiGroup;
    private final String resourceName;
    private final List<String> verbs;

    public KubernetesPermission(String apiGroup, String resourceName, String... verbs) {
        this.apiGroup = apiGroup;
        this.resourceName = resourceName;
        this.verbs = Arrays.asList(verbs);
    }

    public String getApiGroup() {
        return apiGroup;
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<String> getVerbs() {
        return verbs;
    }
}
