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

package org.entando.kubernetes.controller.support.client;

/*
 * FABRIC8 6.x MIGRATION NOTE:
 * ===========================
 * This class has been REMOVED in Fabric8 6.x migration.
 * The "Doneable" pattern no longer exists in Fabric8 6.x.
 *
 * OLD PATTERN (Fabric8 5.x):
 * --------------------------
 * DoneableIngress ingress = new DoneableIngress(ingressInstance, action);
 * ingress.editSpec().addToRules(rule).endSpec();
 * Ingress result = ingress.done();
 *
 * NEW PATTERN (Fabric8 6.x):
 * --------------------------
 * Ingress ingress = new IngressBuilder(existingIngress)
 *     .editOrNewSpec()
 *         .addToRules(rule)
 *     .endSpec()
 *     .build();
 *
 * Files that need updating:
 * - IngressCreator.java
 * - DefaultIngressClient.java
 * - Any other files using DoneableIngress
 */

// COMMENTED OUT - OLD FABRIC8 5.x CODE
// Keeping for reference during migration

/*
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressFluentImpl;  // REMOVED in Fabric8 6.x
import java.util.function.UnaryOperator;

public class DoneableIngress extends IngressFluentImpl<DoneableIngress> {

    private final UnaryOperator<Ingress> action;
    private final Object hashCode = new Object();

    public DoneableIngress(Ingress ingress, UnaryOperator<Ingress> action) {
        super(ingress);
        this.action = action;
    }

    public Ingress done() {
        Ingress built = new Ingress(getApiVersion(), getKind(), buildMetadata(), buildSpec(), buildStatus());
        return withDiagnostics(() -> action.apply(built), () -> built);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return hashCode.hashCode();
    }
}
*/