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

import java.util.ArrayList;

public abstract class EntandoIngressingDeploymentSpecFluent<F extends EntandoIngressingDeploymentSpecFluent<F>> extends
        EntandoDeploymentSpecFluent<F> implements EntandoIngressingDeploymentSpecBaseFluent<F> {

    protected String ingressHostName;
    protected String tlsSecretName;
    protected DbmsVendor dbms;

    protected EntandoIngressingDeploymentSpecFluent(EntandoIngressingDeploymentSpec spec) {
        super(spec);
        this.ingressHostName = spec.getIngressHostName().orElse(null);
        this.tlsSecretName = spec.getTlsSecretName().orElse(null);
        this.dbms = spec.getDbms().orElse(null);
    }

    protected EntandoIngressingDeploymentSpecFluent() {
        this.environmentVariables = new ArrayList<>();
    }

    public final F withTlsSecretName(String tlsSecretName) {
        this.tlsSecretName = tlsSecretName;
        return thisAsF();
    }

    public final F withIngressHostName(String ingressHostName) {
        this.ingressHostName = ingressHostName;
        return thisAsF();
    }

    public final F withDbms(DbmsVendor dbms) {
        this.dbms = dbms;
        return thisAsF();
    }
}
