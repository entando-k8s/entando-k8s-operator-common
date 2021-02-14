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

package org.entando.kubernetes.model.externaldatabase;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.entando.kubernetes.model.EntandoFluent;
import org.entando.kubernetes.model.EntandoIngressingDeploymentBaseFluent;

public class EntandoDatabaseServiceFluent<F extends EntandoDatabaseServiceFluent<F>>
        extends EntandoFluent<F>
        implements EntandoIngressingDeploymentBaseFluent<F, NestedEntandoDatabaseServiceFluent<F>> {

    protected EntandoDatabaseServiceSpecBuilder spec;

    protected EntandoDatabaseServiceFluent() {
        this(new ObjectMetaBuilder(), new EntandoDatabaseServiceSpecBuilder());
    }

    protected EntandoDatabaseServiceFluent(EntandoDatabaseServiceSpec spec, ObjectMeta objectMeta) {
        this(new ObjectMetaBuilder(objectMeta), new EntandoDatabaseServiceSpecBuilder(spec));
    }

    private EntandoDatabaseServiceFluent(ObjectMetaBuilder metadata, EntandoDatabaseServiceSpecBuilder spec) {
        super(metadata);
        this.spec = spec;
    }

    public NestedEntandoDatabaseServiceFluent<F> editSpec() {
        return new NestedEntandoDatabaseServiceFluent<>(thisAsF(), this.spec.build());
    }

    public NestedEntandoDatabaseServiceFluent<F> withNewSpec() {
        return new NestedEntandoDatabaseServiceFluent<>(thisAsF());
    }

    public F withSpec(EntandoDatabaseServiceSpec spec) {
        this.spec = new EntandoDatabaseServiceSpecBuilder(spec);
        return thisAsF();
    }

}
