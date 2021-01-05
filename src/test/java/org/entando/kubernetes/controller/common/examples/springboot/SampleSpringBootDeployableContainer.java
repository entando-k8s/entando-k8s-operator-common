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

package org.entando.kubernetes.controller.common.examples.springboot;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public class SampleSpringBootDeployableContainer<T extends EntandoBaseCustomResource> implements SpringBootDeployableContainer,
        ParameterizableContainer, PersistentVolumeAware, ConfigurableResourceContainer {

    public static final String MY_IMAGE = "entando/entando-k8s-service";
    public static final String MY_WEB_CONTEXT = "/k8s";
    private final T customResource;
    private final KeycloakConnectionConfig keycloakConnectionConfig;
    private Map<String, DatabaseSchemaCreationResult> dbSchemas;

    public SampleSpringBootDeployableContainer(T customResource, KeycloakConnectionConfig keycloakConnectionConfig) {
        this.customResource = customResource;
        this.keycloakConnectionConfig = keycloakConnectionConfig;
    }

    @Override
    public String getWebContextPath() {
        return MY_WEB_CONTEXT;
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath() + "/actuator/health");
    }

    @Override
    public String determineImageToUse() {
        return MY_IMAGE;
    }

    @Override
    public String getNameQualifier() {
        return KubeUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8084;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        return new ArrayList<>();
    }

    @Override
    public KeycloakConnectionConfig getKeycloakConnectionConfig() {
        return this.keycloakConnectionConfig;
    }

    @Override
    public KeycloakClientConfig getKeycloakClientConfig() {
        return new KeycloakClientConfig(determineRealm(),
                customResource.getMetadata().getName() + "-" + getNameQualifier(),
                customResource.getMetadata().getName() + "-" + getNameQualifier());
    }

    @Override
    public KeycloakAwareSpec getKeycloakAwareSpec() {
        return (KeycloakAwareSpec) customResource.getSpec();
    }

    @Override
    public List<String> getDbSchemaQualifiers() {
        return Arrays.asList(getNameQualifier() + "db");
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas) {
        this.dbSchemas = dbSchemas;
        return Optional.empty();
    }

    @Override
    public DatabaseSchemaCreationResult getDatabaseSchema() {
        return this.dbSchemas.get(getNameQualifier() + "db");
    }

    @Override
    public EntandoIngressingDeploymentSpec getCustomResourceSpec() {
        return customResource.getSpec() instanceof EntandoIngressingDeploymentSpec ? (EntandoIngressingDeploymentSpec) customResource
                .getSpec() : null;
    }

    @Override
    public String getVolumeMountPath() {
        return "/entando-data";
    }
}
