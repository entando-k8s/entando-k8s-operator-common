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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.FluentTernary;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.SecretCreator;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.database.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.DbAware;
import org.entando.kubernetes.controller.spi.IngressingContainer;
import org.entando.kubernetes.controller.spi.TlsAware;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class TestServerDeployableContainer implements IngressingContainer, DbAware, TlsAware {

    private static final String DEFAULT_KEYCLOAK_IMAGE_NAME = "entando/entando-keycloak:6.0.0-SNAPSHOT";

    private final EntandoKeycloakServer keycloakServer;
    private Map<String, DatabaseSchemaCreationResult> dbSchemas;

    public TestServerDeployableContainer(EntandoKeycloakServer keycloakServer) {
        this.keycloakServer = keycloakServer;
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
        return KubeUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPort() {
        return 8080;
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("KEYCLOAK_USER", null, KubeUtils.secretKeyRef(secretName(keycloakServer), KubeUtils.USERNAME_KEY)));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", null, KubeUtils.secretKeyRef(secretName(keycloakServer), KubeUtils.PASSSWORD_KEY)));
        DatabaseSchemaCreationResult databaseSchemaCreationResult = dbSchemas.get("db");
        vars.add(new EnvVar("DB_ADDR", databaseSchemaCreationResult.getInternalServiceHostname(), null));
        vars.add(new EnvVar("DB_PORT", databaseSchemaCreationResult.getPort(), null));
        vars.add(new EnvVar("DB_DATABASE", databaseSchemaCreationResult.getDatabase(), null));
        vars.add(new EnvVar("DB_PASSWORD", null, databaseSchemaCreationResult.getPasswordRef()));
        vars.add(new EnvVar("DB_USER", null, databaseSchemaCreationResult.getUsernameRef()));
        vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databaseSchemaCreationResult), null));
        vars.add(new EnvVar("DB_SCHEMA", databaseSchemaCreationResult.getSchemaName(), null));
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
    }

    @Override
    public void addTlsVariables(List<EnvVar> vars) {
        String certFiles = String.join(" ",
                EntandoOperatorConfig.getCertificateAuthorityCertPaths().stream()
                        .map(path -> SecretCreator.standardCertPathOf(path.getFileName().toString()))
                        .collect(Collectors.toList()));
        vars.add(new EnvVar("X509_CA_BUNDLE",
                "/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt /var/run/secrets/kubernetes.io/serviceaccount/ca.crt "
                        + certFiles, null));
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaCreationResult databaseSchemaCreationResult) {
        return FluentTernary.use("postgres").when(databaseSchemaCreationResult.getVendor() == DbmsDockerVendorStrategy.POSTGRESQL)
                .orElse(databaseSchemaCreationResult.getVendor().getName());
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
    public void addDatabaseConnectionVariables(List<EnvVar> envVars) {

    }

}
