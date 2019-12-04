package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface OperationsSupplier<R extends EntandoCustomResource, L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>> {

    CustomResourceOperationsImpl<R, L, D> get(KubernetesClient client);
}
