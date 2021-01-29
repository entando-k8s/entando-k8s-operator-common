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

package org.entando.kubernetes.controller.spi.deployable;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.EntandoResourceRequirements;
import org.entando.kubernetes.model.SpecHasIngress;

public interface IngressingDeployable<T extends ExposedDeploymentResult<T>, S extends EntandoIngressingDeploymentSpec> extends
        Deployable<T, S>,
        Ingressing<IngressingContainer> {

    default Optional<String> getFileUploadLimit() {
        return ((EntandoDeploymentSpec) getIngressingResource().getSpec()).getResourceRequirements()
                .flatMap(EntandoResourceRequirements::getFileUploadLimit);
    }

    default boolean isTlsSecretSpecified() {
        return getIngressingResource().getTlsSecretName().isPresent();
    }

    @JsonIgnore
    default SpecHasIngress getIngressingResource() {
        return (SpecHasIngress) getCustomResource();
    }

    @Override
    @JsonIgnore
    default List<IngressingContainer> getIngressingContainers() {
        return getContainers().stream()
                .filter(IngressingContainer.class::isInstance)
                .map(IngressingContainer.class::cast)
                .collect(toList());
    }

}
