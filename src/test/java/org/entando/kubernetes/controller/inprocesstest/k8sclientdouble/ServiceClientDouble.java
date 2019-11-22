package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceClientDouble extends AbstractK8SClientDouble implements ServiceClient {

    public ServiceClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Service createService(EntandoCustomResource peerInNamespace, Service service) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putService(service.getMetadata().getName(), service);
        return service;
    }

    @Override
    public void createEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        getNamespace(peerInNamespace).putEndpoints(endpoints);
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getService(name);
    }

}
