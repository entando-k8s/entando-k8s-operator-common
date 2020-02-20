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
