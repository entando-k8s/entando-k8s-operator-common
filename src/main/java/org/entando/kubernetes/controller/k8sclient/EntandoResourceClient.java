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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Optional;
import org.entando.kubernetes.controller.ExposedService;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.ClusterInfrastructureAwareSpec;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public interface EntandoResourceClient {

    String getNamespace();

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    <T extends EntandoCustomResource> T patchEntandoResource(T r);

    void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor);

    <T extends KeycloakAwareSpec> KeycloakConnectionConfig findKeycloak(EntandoBaseCustomResource<T> resource);

    Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoBaseCustomResource<?> peerInNamespace);

    <T extends ClusterInfrastructureAwareSpec> Optional<InfrastructureConfig> findInfrastructureConfig(
            EntandoBaseCustomResource<T> resource);

    ExposedService loadExposedService(EntandoCustomResource resource);

    default EntandoApp loadEntandoApp(String namespace, String name) {
        return load(EntandoApp.class, namespace, name);
    }

    default EntandoPlugin loadEntandoPlugin(String namespace, String name) {
        return load(EntandoPlugin.class, namespace, name);
    }

    DoneableConfigMap loadDefaultConfigMap();

    default <T extends KeycloakAwareSpec> Optional<ResourceReference> determineKeycloakToUse(EntandoBaseCustomResource<T> resource) {
        ResourceReference resourceReference = null;
        if (resource.getSpec().getKeycloakToUse().isPresent()) {
            resourceReference = new ResourceReference(
                    resource.getSpec().getKeycloakToUse().get().getNamespace().orElse(null),
                    resource.getSpec().getKeycloakToUse().get().getName());
        } else {
            Optional<EntandoKeycloakServer> keycloak = findKeycloakInNamespace(resource);
            if (keycloak.isPresent()) {
                resourceReference = new ResourceReference(
                        keycloak.get().getMetadata().getNamespace(),
                        keycloak.get().getMetadata().getName());
            } else {
                DoneableConfigMap configMapResource = loadDefaultConfigMap();
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY),
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY));

            }
        }
        return refineResourceReference(resourceReference, resource.getMetadata());
    }

    default <T extends ClusterInfrastructureAwareSpec> Optional<ResourceReference> determineClusterInfrastructureToUse(
            EntandoBaseCustomResource<T> resource) {
        ResourceReference resourceReference = null;
        if (resource.getSpec().getClusterInfrastructureToUse().isPresent()) {
            //Not ideal. GetName should not return null.
            resourceReference = new ResourceReference(
                    resource.getSpec().getClusterInfrastructureToUse().get().getNamespace().orElse(null),
                    resource.getSpec().getClusterInfrastructureToUse().get().getName());
        } else {
            Optional<EntandoClusterInfrastructure> clusterInfrastructure = findClusterInfrastructureInNamespace(resource);
            if (clusterInfrastructure.isPresent()) {
                resourceReference = new ResourceReference(
                        clusterInfrastructure.get().getMetadata().getNamespace(),
                        clusterInfrastructure.get().getMetadata().getName());
            } else {
                DoneableConfigMap configMapResource = loadDefaultConfigMap();
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY),
                        configMapResource.getData().get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY));

            }
        }
        return refineResourceReference(resourceReference, resource.getMetadata());
    }

    <T extends ClusterInfrastructureAwareSpec> Optional<EntandoClusterInfrastructure> findClusterInfrastructureInNamespace(
            EntandoBaseCustomResource<T> resource);

    default Optional<ResourceReference> refineResourceReference(ResourceReference resourceReference, ObjectMeta metadata) {
        if (resourceReference.getName() == null) {
            //no valid resource reference in any config anywhere. Return empty
            return Optional.empty();
        } else {
            //Default an empty namespace to the resource's own namespace
            return Optional.of(new ResourceReference(
                    resourceReference.getNamespace().orElse(metadata.getNamespace()),
                    resourceReference.getName()));
        }
    }

}
