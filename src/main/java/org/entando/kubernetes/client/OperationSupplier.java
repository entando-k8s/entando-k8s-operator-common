package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;

public interface OperationSupplier {

    CustomResourceOperationsImpl get(KubernetesClient client);
}
