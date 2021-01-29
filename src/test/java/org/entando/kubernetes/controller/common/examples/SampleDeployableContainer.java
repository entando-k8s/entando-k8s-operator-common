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

package org.entando.kubernetes.controller.common.examples;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAware;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.common.FluentTernary;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;

public class SampleDeployableContainer<S extends EntandoDeploymentSpec> implements IngressingContainer, DbAware, TlsAware,
        PersistentVolumeAware, ParameterizableContainer {

    public static final String DEFAULT_IMAGE_NAME = "entando/entando-keycloak:6.0.0-SNAPSHOT";
    public static final String VAR_LIB_MYDATA = "/var/lib/mydata";

    private final EntandoBaseCustomResource<S> entandoResource;
    private final List<DatabaseSchemaConnectionInfo> databaseSchemaInfo;

    public SampleDeployableContainer(EntandoBaseCustomResource<S> entandoResource, DatabaseServiceResult databaseServiceResult) {
        this.entandoResource = entandoResource;
        if (databaseServiceResult == null) {
            this.databaseSchemaInfo = Collections.emptyList();
        } else {
            this.databaseSchemaInfo = DbAware
                    .buildDatabaseSchemaConnectionInfo(entandoResource, databaseServiceResult, Collections.singletonList("db"));
        }
    }

    public static String secretName(EntandoCustomResource entandoResource) {
        return entandoResource.getMetadata().getName() + "-admin-secret";
    }

    @Override
    public String determineImageToUse() {
        return DEFAULT_IMAGE_NAME;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8080;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("KEYCLOAK_USER", null, SecretUtils.secretKeyRef(secretName(entandoResource), SecretUtils.USERNAME_KEY)));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", null, SecretUtils.secretKeyRef(secretName(entandoResource), SecretUtils.PASSSWORD_KEY)));
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        DatabaseSchemaConnectionInfo databseSchemaConnectionInfo = databaseSchemaInfo.get(0);
        vars.add(new EnvVar("DB_ADDR", databseSchemaConnectionInfo.getInternalServiceHostname(), null));
        vars.add(new EnvVar("DB_PORT", databseSchemaConnectionInfo.getPort(), null));
        vars.add(new EnvVar("DB_DATABASE", databseSchemaConnectionInfo.getDatabase(), null));
        vars.add(new EnvVar("DB_PASSWORD", null, databseSchemaConnectionInfo.getPasswordRef()));
        vars.add(new EnvVar("DB_USER", null, databseSchemaConnectionInfo.getUsernameRef()));
        vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databseSchemaConnectionInfo), null));
        vars.add(new EnvVar("DB_SCHEMA", databseSchemaConnectionInfo.getSchemaName(), null));
        return vars;
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.databaseSchemaInfo;
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaConnectionInfo databseSchemaConnectionInfo) {
        return FluentTernary.use("postgres").when(databseSchemaConnectionInfo.getVendor().getVendorConfig() == DbmsVendorConfig.POSTGRESQL)
                .orElse(databseSchemaConnectionInfo.getVendor().getVendorConfig().getName());
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }

    @Override
    public String getVolumeMountPath() {
        return VAR_LIB_MYDATA;
    }

    protected EntandoIngressingDeploymentSpec getCustomResourceSpec() {
        return (EntandoIngressingDeploymentSpec) entandoResource.getSpec();
    }

    @Override
    public List<EnvVar> getEnvironmentVariableOverrides() {
        return getCustomResourceSpec().getEnvironmentVariables();
    }
}
