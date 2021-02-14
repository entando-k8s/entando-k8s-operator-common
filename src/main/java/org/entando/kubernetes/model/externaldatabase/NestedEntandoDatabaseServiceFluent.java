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

import org.entando.kubernetes.model.NestedIngressingDeploymentSpecFluent;

public class NestedEntandoDatabaseServiceFluent<F extends EntandoDatabaseServiceFluent<F>>
        extends EntandoDatabaseServiceSpecFluent<NestedEntandoDatabaseServiceFluent<F>>
        implements NestedIngressingDeploymentSpecFluent<F, NestedEntandoDatabaseServiceFluent<F>> {

    private final F parentBuilder;

    NestedEntandoDatabaseServiceFluent(F parentBuilder, EntandoDatabaseServiceSpec item) {
        super(item);
        this.parentBuilder = parentBuilder;
    }

    public NestedEntandoDatabaseServiceFluent(F parentBuilder) {
        super();
        this.parentBuilder = parentBuilder;
    }

    @Override
    public F and() {
        return parentBuilder.withSpec(this.build());
    }

    public F endSpec() {
        return this.and();
    }

}
