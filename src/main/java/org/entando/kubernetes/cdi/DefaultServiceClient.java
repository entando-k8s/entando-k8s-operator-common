package org.entando.kubernetes.cdi;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.DoneableEndpoints;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultServiceClient implements ServiceClient {

    private static final Logger LOGGER = Logger.getLogger(DefaultServiceClient.class.getName());

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultServiceClient(DefaultKubernetesClient client) {
        this.client = client;
    }

    @Override
    public void createEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        //TODO remove the namespace overriding once we create delegate services from the correct context (the App)
        String namespace = ofNullable(endpoints.getMetadata().getNamespace())
                .orElse(peerInNamespace.getMetadata().getNamespace());
        FixedEndpointsOperation eo = new FixedEndpointsOperation(client.getHttpClient(), client.getConfiguration(), namespace);
        //All of this is because of the Fabric8 bug - createOrReplace fails
        FixedEndpointsOperation endpointsDoneableEndpointsResource = eo.withName(endpoints.getMetadata().getName());
        if (endpointsDoneableEndpointsResource.get() != null) {
            endpointsDoneableEndpointsResource.delete();
        }
        eo.create(endpoints);
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
