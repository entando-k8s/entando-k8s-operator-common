/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import java.util.Optional;
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

    default int getReplicas() {
        return 1;
    }

    default Optional<Long> getFileSystemUserAndGroupId() {
        return Optional.empty();
    }
}
