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

package org.entando.kubernetes.controller.spi.common;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import java.io.Serializable;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class ResourceUtils {

    private ResourceUtils() {

    }

    public static <S extends Serializable> OwnerReference buildOwnerReference(EntandoBaseCustomResource<S> entandoCustomResource) {
        return new OwnerReferenceBuilder()
                .withApiVersion(entandoCustomResource.getApiVersion())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .withKind(entandoCustomResource.getKind())
                .withName(entandoCustomResource.getMetadata().getName())
                .withUid(entandoCustomResource.getMetadata().getUid()).build();
    }
}
