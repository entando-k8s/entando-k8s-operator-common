package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface PatchableClient {

    @SuppressWarnings("unchecked")
    default <T extends HasMetadata> T createOrPatch(EntandoCustomResource peerInNamespace, T deployment,
            MixedOperation<T, ? extends KubernetesResourceList<T>, ? extends Doneable<T>, ?
                    extends Resource<T, ? extends Doneable<T>>> operation) {
        Resource<T, ? extends Doneable<T>> d = operation
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(deployment.getMetadata().getName());
        if (d.get() == null) {
            return operation.inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            return d.patch(deployment);
        }
    }

}
