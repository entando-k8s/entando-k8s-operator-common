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

package org.entando.kubernetes.model.keycloakserver;

import io.fabric8.kubernetes.api.builder.Fluent;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.entando.kubernetes.model.EntandoFluent;
import org.entando.kubernetes.model.EntandoIngressingDeploymentBaseFluent;

public class EntandoKeycloakServerFluent<F extends EntandoKeycloakServerFluent<F>>
        extends EntandoFluent<F>
        implements Fluent<F>, EntandoIngressingDeploymentBaseFluent<F, NestedEntandoKeycloakServerSpecFluent<F>> {

    protected EntandoKeycloakServerSpecBuilder spec;

    protected EntandoKeycloakServerFluent() {
        this(new ObjectMetaBuilder(), new EntandoKeycloakServerSpecBuilder());
    }

    protected EntandoKeycloakServerFluent(EntandoKeycloakServerSpec spec, ObjectMeta objectMeta) {
        this(new ObjectMetaBuilder(objectMeta), new EntandoKeycloakServerSpecBuilder(spec));
    }

    private EntandoKeycloakServerFluent(ObjectMetaBuilder metadata, EntandoKeycloakServerSpecBuilder spec) {
        super(metadata);
        this.spec = spec;
    }

    public F withSpec(EntandoKeycloakServerSpec spec) {
        this.spec = new EntandoKeycloakServerSpecBuilder(spec);
        return thisAsF();
    }

    public NestedEntandoKeycloakServerSpecFluent<F> withNewSpec() {
        return new NestedEntandoKeycloakServerSpecFluent<>(thisAsF());
    }

    @Override
    public NestedEntandoKeycloakServerSpecFluent<F> editSpec() {
        return new NestedEntandoKeycloakServerSpecFluent<>(thisAsF(), this.spec.build());
    }

}
