package org.entando.kubernetes.controller.spi;

import static java.util.stream.Collectors.toList;

import java.util.List;
import org.entando.kubernetes.model.HasIngress;

public interface IngressingDeployable<T extends ServiceResult> extends Deployable<T>, Ingressing<IngressingContainer> {

    default boolean isTlsSecretSpecified() {
        return getIngressingResource().getTlsSecretName().isPresent();
    }

    default HasIngress getIngressingResource() {
        return (HasIngress) getCustomResource();
    }

    @Override
    default List<IngressingContainer> getIngressingContainers() {
        return getContainers().stream()
                .filter(IngressingContainer.class::isInstance)
                .map(IngressingContainer.class::cast)
                .collect(toList());
    }

}
