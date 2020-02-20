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
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import javax.enterprise.event.Observes;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class SampleController<T extends EntandoBaseCustomResource> extends AbstractDbAwareController<T> {

    public SampleController(KubernetesClient kubernetesClient) {
        super(kubernetesClient, false);
    }

    public SampleController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        super(k8sClient, keycloakClient);
    }

    public void onStartup(@Observes StartupEvent event) {
        processCommand();
    }

    protected void synchronizeDeploymentState(T newEntandoResource) {
        // Create database for Keycloak
        EntandoDeploymentSpec spec = resolveSpec(newEntandoResource);
        DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newEntandoResource, spec.getDbms().get(),
                "db");
        // Create the Keycloak service using the provided database
        KeycloakConnectionConfig keycloakConnectionConfig = k8sClient.entandoResources().findKeycloak(() -> Optional.empty());
        Deployable<ServiceDeploymentResult> keycloakDeployable = createDeployable(newEntandoResource, databaseServiceResult,
                keycloakConnectionConfig);
        DeployCommand<ServiceDeploymentResult> keycloakCommand = new DeployCommand<>(keycloakDeployable);
        ServiceDeploymentResult keycloakDeploymentResult = keycloakCommand.execute(k8sClient, Optional.of(keycloakClient));
        k8sClient.entandoResources().updateStatus(newEntandoResource, keycloakCommand.getStatus());
    }

    private EntandoDeploymentSpec resolveSpec(T r) {
        Object spec = null;
        try {
            spec = r.getClass().getMethod("getSpec").invoke(r);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return (EntandoDeploymentSpec) spec;
    }

    protected Deployable<ServiceDeploymentResult> createDeployable(T newEntandoKeycloakServer,
            DatabaseServiceResult databaseServiceResult,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        return new SamplePublicIngressingDbAwareDeployable<>(newEntandoKeycloakServer, databaseServiceResult, keycloakConnectionConfig);
    }

}
