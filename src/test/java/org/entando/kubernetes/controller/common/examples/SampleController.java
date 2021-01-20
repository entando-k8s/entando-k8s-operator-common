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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.IngressingDeployCommand;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.ServiceDeploymentResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public abstract class SampleController<S extends KeycloakAwareSpec, C extends EntandoBaseCustomResource<S>,
        R extends ServiceDeploymentResult<R>> extends
        AbstractDbAwareController<S, C> {

    public SampleController(KubernetesClient kubernetesClient) {
        super(kubernetesClient, false);
    }

    public SampleController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        super(k8sClient, keycloakClient);
    }

    public void onStartup(StartupEvent event) {
        processCommand();
    }

    @SuppressWarnings("unchecked")
    protected void synchronizeDeploymentState(C newEntandoResource) {
        // Create database for Keycloak
        EntandoIngressingDeploymentSpec spec = newEntandoResource.getSpec();
        DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newEntandoResource, spec.getDbms().get()
        );
        // Create the Keycloak service using the provided database
        KeycloakConnectionConfig keycloakConnectionConfig = null;
        keycloakConnectionConfig = k8sClient.entandoResources()
                .findKeycloak((EntandoBaseCustomResource<? extends KeycloakAwareSpec>) newEntandoResource);
        Deployable<R, S> deployable = createDeployable(newEntandoResource, databaseServiceResult,
                keycloakConnectionConfig);
        DeployCommand<R, S> deployCommand;
        if (deployable instanceof IngressingDeployable) {
            deployCommand = new IngressingDeployCommand((IngressingDeployable) deployable);
        } else {
            deployCommand = new DeployCommand<>(deployable);
        }
        R keycloakDeploymentResult = deployCommand.execute(k8sClient, Optional.of(keycloakClient));
        k8sClient.entandoResources().updateStatus(newEntandoResource, keycloakDeploymentResult.getStatus());
    }

    protected abstract Deployable<R, S> createDeployable(C newEntandoKeycloakServer,
            DatabaseServiceResult databaseServiceResult,
            KeycloakConnectionConfig keycloakConnectionConfig);

}
