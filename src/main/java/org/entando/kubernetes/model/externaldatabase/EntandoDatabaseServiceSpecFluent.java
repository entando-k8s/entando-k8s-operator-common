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

import static org.entando.kubernetes.model.Coalescence.coalesce;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoDeploymentSpecFluent;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpecBaseFluent;

public abstract class EntandoDatabaseServiceSpecFluent<F extends EntandoDatabaseServiceSpecFluent<F>>
        extends EntandoDeploymentSpecFluent<F>
        implements EntandoIngressingDeploymentSpecBaseFluent<F> {

    private String databaseName;
    private DbmsVendor dbms;
    private String host;
    private Integer port;
    private String secretName;
    private Map<String, String> jdbcParameters;
    private String tablespace;
    private Boolean createDeployment;

    protected EntandoDatabaseServiceSpecFluent(EntandoDatabaseServiceSpec spec) {
        super(spec);
        this.databaseName = spec.getDatabaseName().orElse(null);
        this.dbms = spec.getDbms();
        this.host = spec.getHost().orElse(null);
        this.port = spec.getPort().orElse(null);
        this.secretName = spec.getSecretName().orElse(null);
        this.tablespace = spec.getTablespace().orElse(null);
        this.createDeployment = spec.getCreateDeployment().orElse(null);
        this.jdbcParameters = coalesce(spec.getJdbcParameters(), this.jdbcParameters);
    }

    protected EntandoDatabaseServiceSpecFluent() {

    }

    public EntandoDatabaseServiceSpec build() {
        return new EntandoDatabaseServiceSpec(
                dbms,
                host,
                port,
                databaseName,
                tablespace,
                secretName,
                createDeployment,
                jdbcParameters,
                replicas,
                serviceAccountToUse,
                environmentVariables,
                resourceRequirements);
    }

    public F withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return thisAsF();
    }

    public F withCreateDeployment(Boolean createDeployment) {
        this.createDeployment = createDeployment;
        return thisAsF();
    }

    public F withDbms(DbmsVendor dbms) {
        this.dbms = dbms;
        return thisAsF();
    }

    public F withHost(String host) {
        this.host = host;
        return thisAsF();
    }

    public F withPort(Integer port) {
        this.port = port;
        return thisAsF();
    }

    public F withSecretName(String secretName) {
        this.secretName = secretName;
        return thisAsF();
    }

    public F withJdbcParameters(Map<String, String> jdbcParameters) {
        this.jdbcParameters = new ConcurrentHashMap<>(jdbcParameters);
        return thisAsF();
    }

    public F addToJdbcParameters(String name, String value) {
        if (jdbcParameters == null) {
            jdbcParameters = new ConcurrentHashMap<>();
        }
        this.jdbcParameters.put(name, value);
        return thisAsF();
    }

    public F withTablespace(String tablespace) {
        this.tablespace = tablespace;
        return thisAsF();
    }

    @Override
    public F withTlsSecretName(String tlsSecretName) {
        return thisAsF();
    }

    @Override
    public F withIngressHostName(String ingressHostName) {
        return thisAsF();
    }

}
