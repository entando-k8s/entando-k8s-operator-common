package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface ServiceClient {

    Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service service);

    Service loadService(EntandoCustomResource peerInNamespace, String name);

    void createOrReplaceEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints);

}
