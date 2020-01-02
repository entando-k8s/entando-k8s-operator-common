package org.entando.kubernetes.client;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultServiceClient implements ServiceClient {

    private final KubernetesClient client;

    public DefaultServiceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void createEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        //TODO remove the namespace overriding once we create delegate services from the correct context (the App)
        String namespace = ofNullable(endpoints.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        if (client.endpoints().inNamespace(namespace).withName(endpoints.getMetadata().getName()).get() != null) {
            client.endpoints().inNamespace(namespace).withName(endpoints.getMetadata().getName()).delete();
        }
        client.endpoints().inNamespace(namespace).create(endpoints);
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    @Override
    public Service createService(EntandoCustomResource peerInNamespace, Service service) {
        //TODO remove once we create delegate services from the correct context (the App)
        String namespace = ofNullable(service.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        return client.services().inNamespace(namespace).createOrReplace(service);
    }
}
