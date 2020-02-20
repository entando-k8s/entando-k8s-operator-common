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

import static org.entando.kubernetes.controller.KubeUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class SamplePublicIngressingDbAwareDeployable<T extends EntandoBaseCustomResource> extends
        SampleIngressingDbAwareDeployable<T> implements PublicIngressingDeployable<ServiceDeploymentResult>,
        Secretive {

    public static final String SAMPLE_PUBLIC_CLIENT = "sample-public-client";
    private final Secret sampleSecret;
    private KeycloakConnectionConfig keycloakConnectionConfig;

    public SamplePublicIngressingDbAwareDeployable(T entandoResource, DatabaseServiceResult databaseServiceResult,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        super(entandoResource, databaseServiceResult);
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        sampleSecret = generateSecret(this.entandoResource, secretName(this.entandoResource),
                "entando_keycloak_admin");
    }

    public static <T extends EntandoBaseCustomResource> String secretName(T resource) {
        return resource.getMetadata().getName() + "-admin-secret";
    }

    protected List<DeployableContainer> createContainers(T entandoResource) {
        return Arrays.asList(new SampleDeployableContainer<>(entandoResource));
    }

    @Override
    public List<Secret> buildSecrets() {
        return Arrays.asList(sampleSecret);
    }

    @Override
    public String getPublicKeycloakClientId() {
        return SAMPLE_PUBLIC_CLIENT;
    }

    @Override
    public KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return keycloakConnectionConfig;
    }
}
