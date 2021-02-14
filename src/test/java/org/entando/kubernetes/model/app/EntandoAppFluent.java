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

package org.entando.kubernetes.model.app;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.entando.kubernetes.model.EntandoFluent;
import org.entando.kubernetes.model.EntandoIngressingDeploymentBaseFluent;

public class EntandoAppFluent<F extends EntandoAppFluent<F>>
        extends EntandoFluent<F>
        implements EntandoIngressingDeploymentBaseFluent<F, NestedEntandoAppSpecFluent<F>> {

    protected EntandoAppSpecBuilder spec;

    protected EntandoAppFluent() {
        this(new ObjectMetaBuilder(), new EntandoAppSpecBuilder());
    }

    protected EntandoAppFluent(EntandoAppSpec spec, ObjectMeta objectMeta) {
        this(new ObjectMetaBuilder(objectMeta), new EntandoAppSpecBuilder(spec));
    }

    private EntandoAppFluent(ObjectMetaBuilder metadata, EntandoAppSpecBuilder spec) {
        super(metadata);
        this.spec = spec;
    }

    public NestedEntandoAppSpecFluent<F> editSpec() {
        return new NestedEntandoAppSpecFluent<>(thisAsF(), this.spec.build());
    }

    public NestedEntandoAppSpecFluent<F> withNewSpec() {
        return new NestedEntandoAppSpecFluent<>(thisAsF());
    }

    public F withSpec(EntandoAppSpec spec) {
        this.spec = new EntandoAppSpecBuilder(spec);
        return thisAsF();
    }

}
