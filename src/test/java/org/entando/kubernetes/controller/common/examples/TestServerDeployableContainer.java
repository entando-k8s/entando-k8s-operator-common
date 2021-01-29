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
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAware;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.FluentTernary;
import org.entando.kubernetes.controller.support.creators.SecretCreator;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class TestServerDeployableContainer implements IngressingContainer, DbAware, TlsAware {

    private static final String DEFAULT_KEYCLOAK_IMAGE_NAME = "entando/entando-keycloak:6.0.0-SNAPSHOT";

    private final EntandoKeycloakServer keycloakServer;
    private final List<DatabaseSchemaConnectionInfo> dbSchemaInfo;

    public TestServerDeployableContainer(EntandoKeycloakServer keycloakServer,
            DatabaseServiceResult databaseServiceResult) {
        this.keycloakServer = keycloakServer;
        this.dbSchemaInfo = DbAware
                .buildDatabaseSchemaConnectionInfo(keycloakServer, databaseServiceResult, Collections.singletonList("db"));

    }

    public static String secretName(EntandoKeycloakServer keycloakServer) {
        return keycloakServer.getMetadata().getName() + "-admin-secret";
    }

    @Override
    public String determineImageToUse() {
        return DEFAULT_KEYCLOAK_IMAGE_NAME;
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
        vars.add(new EnvVar("KEYCLOAK_USER", null, SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.USERNAME_KEY)));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", null, SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.PASSSWORD_KEY)));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        DatabaseSchemaConnectionInfo databseSchemaConnectionInfo = dbSchemaInfo.get(0);
        vars.add(new EnvVar("DB_ADDR", databseSchemaConnectionInfo.getInternalServiceHostname(), null));
        vars.add(new EnvVar("DB_PORT", databseSchemaConnectionInfo.getPort(), null));
        vars.add(new EnvVar("DB_DATABASE", databseSchemaConnectionInfo.getDatabase(), null));
        vars.add(new EnvVar("DB_PASSWORD", null, databseSchemaConnectionInfo.getPasswordRef()));
        vars.add(new EnvVar("DB_USER", null, databseSchemaConnectionInfo.getUsernameRef()));
        vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databseSchemaConnectionInfo), null));
        vars.add(new EnvVar("DB_SCHEMA", databseSchemaConnectionInfo.getSchemaName(), null));
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
        return vars;
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.dbSchemaInfo;
    }

    @Override
    public List<EnvVar> getTlsVariables() {
        List<EnvVar> vars = new ArrayList<>();
        String certFiles = String.join(" ",
                EntandoOperatorConfig.getCertificateAuthorityCertPaths().stream()
                        .map(path -> SecretCreator.standardCertPathOf(path.getFileName().toString()))
                        .collect(Collectors.toList()));
        vars.add(new EnvVar("X509_CA_BUNDLE",
                "/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt /var/run/secrets/kubernetes.io/serviceaccount/ca.crt "
                        + certFiles, null));
        return vars;
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaConnectionInfo databseSchemaConnectionInfo) {
        return FluentTernary.use("postgres").when(databseSchemaConnectionInfo.getVendor() == DbmsDockerVendorStrategy.CENTOS_POSTGRESQL)
                .orElse(databseSchemaConnectionInfo.getVendor().getName());
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }

}
