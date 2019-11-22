package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractK8SClientDouble {

    protected static final String CONTROLLER_NAMESPACE = "controller-namespace";
    private final Map<String, NamespaceDouble> namespaces;

    public AbstractK8SClientDouble() {
        this.namespaces = new ConcurrentHashMap<>();
    }

    public AbstractK8SClientDouble(Map<String, NamespaceDouble> namespaces) {
        this.namespaces = namespaces;
        this.namespaces.put(CONTROLLER_NAMESPACE, new NamespaceDouble(CONTROLLER_NAMESPACE));
    }

    protected NamespaceDouble getNamespace(HasMetadata customResource) {
        return getNamespace(customResource.getMetadata().getNamespace());
    }

    protected NamespaceDouble getNamespace(String namespace) {
        NamespaceDouble toUse = this.namespaces.get(namespace);
        if (toUse == null) {
            toUse = new NamespaceDouble(namespace);
            this.namespaces.put(toUse.getName(), toUse);
        }
        return toUse;
    }

    protected Map<String, NamespaceDouble> getNamespaces() {
        return namespaces;
    }
}
