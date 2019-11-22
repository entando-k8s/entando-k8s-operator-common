package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface Deployable<T extends ServiceResult> {

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    List<DeployableContainer> getContainers();

    String getNameQualifier();

    EntandoCustomResource getCustomResource();

    T createResult(Deployment deployment, Service service, Ingress ingress, Pod pod);

    default String getServiceAccountName() {
        return "default";
    }

}
