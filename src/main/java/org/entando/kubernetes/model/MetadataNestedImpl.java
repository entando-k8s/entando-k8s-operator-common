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

package org.entando.kubernetes.model;

import io.fabric8.kubernetes.api.builder.Nested;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaFluentImpl;

public class MetadataNestedImpl<N extends EntandoBaseFluent<N>> extends ObjectMetaFluentImpl<MetadataNestedImpl<N>> implements
        Nested<N> {

    private final N parentBuilder;
    private final ObjectMetaBuilder objectMetaBuilder;

    public MetadataNestedImpl(N parentBuilder, ObjectMeta item) {
        super();
        this.parentBuilder = parentBuilder;
        this.objectMetaBuilder = new ObjectMetaBuilder(this, item);
    }

    public MetadataNestedImpl(N parentBuilder) {
        super();
        this.parentBuilder = parentBuilder;
        this.objectMetaBuilder = new ObjectMetaBuilder(this);
    }

    @Override
    public boolean equals(Object other) {
        //By convention the Sonar way. Nothing to add
        return this == other;
    }

    @Override
    public int hashCode() {
        return this.objectMetaBuilder.hashCode();
    }

    public N endMetadata() {
        return this.and();
    }

    @Override
    public N and() {
        return parentBuilder.withMetadata(objectMetaBuilder.build());
    }
}
