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

import static org.entando.kubernetes.controller.spi.common.SecretUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Collections;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public class SamplePublicIngressingDbAwareDeployable<S extends KeycloakAwareSpec> extends
        SampleIngressingDbAwareDeployable<S> implements PublicIngressingDeployable<SampleExposedDeploymentResult, S>,
        Secretive {

    private final Secret sampleSecret;
    private KeycloakConnectionConfig keycloakConnectionConfig;

    public SamplePublicIngressingDbAwareDeployable(EntandoBaseCustomResource<S> entandoResource,
            DatabaseServiceResult databaseServiceResult,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        super(entandoResource, databaseServiceResult);
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        sampleSecret = generateSecret(this.entandoResource, secretName(this.entandoResource),
                "entando_keycloak_admin");

    }

    public static <T extends EntandoBaseCustomResource<?>> String secretName(T resource) {
        return resource.getMetadata().getName() + "-admin-secret";
    }

    protected List<DeployableContainer> createContainers(EntandoBaseCustomResource<S> entandoResource) {
        return Collections.singletonList(new SampleDeployableContainer<>(entandoResource, databaseServiceResult));
    }

    @Override
    public List<Secret> getSecrets() {
        return Collections.singletonList(sampleSecret);
    }

    @Override
    public KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return keycloakConnectionConfig;
    }
}
