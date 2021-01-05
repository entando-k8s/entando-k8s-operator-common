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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.FluentTernary;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.database.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.DbAware;
import org.entando.kubernetes.controller.spi.IngressingContainer;
import org.entando.kubernetes.controller.spi.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.TlsAware;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;

public class SampleDeployableContainer<S extends EntandoDeploymentSpec> implements IngressingContainer, DbAware, TlsAware,
        PersistentVolumeAware, ParameterizableContainer {

    public static final String DEFAULT_IMAGE_NAME = "entando/entando-keycloak:6.0.0-SNAPSHOT";
    public static final String VAR_LIB_MYDATA = "/var/lib/mydata";

    private final EntandoBaseCustomResource<S> entandoResource;
    private Map<String, DatabaseSchemaCreationResult> dbSchemas;

    public SampleDeployableContainer(EntandoBaseCustomResource<S> entandoResource) {
        this.entandoResource = entandoResource;
    }

    public static <T extends EntandoBaseCustomResource> String secretName(T entandoResource) {
        return entandoResource.getMetadata().getName() + "-admin-secret";
    }

    @Override
    public String determineImageToUse() {
        return DEFAULT_IMAGE_NAME;
    }

    @Override
    public String getNameQualifier() {
        return KubeUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8080;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("KEYCLOAK_USER", null, KubeUtils.secretKeyRef(secretName(entandoResource), KubeUtils.USERNAME_KEY)));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", null, KubeUtils.secretKeyRef(secretName(entandoResource), KubeUtils.PASSSWORD_KEY)));
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        DatabaseSchemaCreationResult databaseSchemaCreationResult = dbSchemas.get("db");
        vars.add(new EnvVar("DB_ADDR", databaseSchemaCreationResult.getInternalServiceHostname(), null));
        vars.add(new EnvVar("DB_PORT", databaseSchemaCreationResult.getPort(), null));
        vars.add(new EnvVar("DB_DATABASE", databaseSchemaCreationResult.getDatabase(), null));
        vars.add(new EnvVar("DB_PASSWORD", null, databaseSchemaCreationResult.getPasswordRef()));
        vars.add(new EnvVar("DB_USER", null, databaseSchemaCreationResult.getUsernameRef()));
        vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databaseSchemaCreationResult), null));
        vars.add(new EnvVar("DB_SCHEMA", databaseSchemaCreationResult.getSchemaName(), null));
        return vars;
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaCreationResult databaseSchemaCreationResult) {
        return FluentTernary.use("postgres").when(databaseSchemaCreationResult.getVendor().getVendorConfig() == DbmsVendorConfig.POSTGRESQL)
                .orElse(databaseSchemaCreationResult.getVendor().getVendorConfig().getName());
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
    public List<String> getDbSchemaQualifiers() {
        return Arrays.asList("db");
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas) {
        this.dbSchemas = dbSchemas;
        return Optional.empty();
    }

    @Override
    public String getVolumeMountPath() {
        return VAR_LIB_MYDATA;
    }

    @Override
    public EntandoIngressingDeploymentSpec getCustomResourceSpec() {
        return (EntandoIngressingDeploymentSpec) entandoResource.getSpec();
    }
}
