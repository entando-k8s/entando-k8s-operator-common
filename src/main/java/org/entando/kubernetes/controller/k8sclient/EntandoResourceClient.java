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

package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import java.util.Optional;
import org.entando.kubernetes.controller.ExposedService;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresClusterInfrastructure;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.KeycloakAwareSpec;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public interface EntandoResourceClient {

    String getNamespace();

    EntandoCustomResource removeFinalizer(EntandoCustomResource r);

    EntandoCustomResource addFinalizer(EntandoCustomResource r);

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    <T extends EntandoCustomResource> T patchEntandoResource(T r);

    void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor);

    <T extends KeycloakAwareSpec> KeycloakConnectionConfig findKeycloak(EntandoBaseCustomResource<T> resource);

    Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoBaseCustomResource<?> peerInNamespace);

    InfrastructureConfig findInfrastructureConfig(RequiresClusterInfrastructure resource);

    ExposedService loadExposedService(EntandoCustomResource resource);

    default EntandoApp loadEntandoApp(String namespace, String name) {
        return load(EntandoApp.class, namespace, name);
    }

    default EntandoPlugin loadEntandoPlugin(String namespace, String name) {
        return load(EntandoPlugin.class, namespace, name);
    }

    DoneableConfigMap loadDefaultConfigMap();

    default <T extends KeycloakAwareSpec> ResourceReference determineKeycloakToUse(EntandoBaseCustomResource<T> resource) {
        ResourceReference resourceReference = null;
        if (resource.getSpec().getKeycloakToUse().isPresent()) {
            resourceReference = new ResourceReference(
                    resource.getSpec().getKeycloakToUse().get().getNamespace(),
                    resource.getSpec().getKeycloakToUse().get().getName());
        } else {
            Optional<EntandoKeycloakServer> keycloak = findKeycloakInNamespace(resource);
            if (keycloak.isPresent()) {
                resourceReference = new ResourceReference(
                        keycloak.get().getMetadata().getNamespace(),
                        keycloak.get().getMetadata().getName());
            } else {
                DoneableConfigMap configMapResource = loadDefaultConfigMap();
                //Nulls are OK because the resulting reference will resolve to the backward compatible "keycloak-admin-secret"
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY),
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY));

            }
        }
        return resourceReference;
    }

}
