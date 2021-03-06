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

package org.entando.kubernetes.controller.support.client.doubles;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.support.client.DoneableConfigMap;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.ClusterInfrastructureAwareSpec;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class EntandoResourceClientDouble extends AbstractK8SClientDouble implements EntandoResourceClient {

    public EntandoResourceClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        this.getNamespace(r).getCustomResources((Class<T>) r.getClass()).put(r.getMetadata().getName(), r);
        return r;
    }

    public void putEntandoDatabaseService(EntandoDatabaseService externalDatabase) {
        createOrPatchEntandoResource(externalDatabase);
    }

    @Override
    public String getNamespace() {
        return CONTROLLER_NAMESPACE;
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource,
            AbstractServerStatus status) {
        customResource.getStatus().putServerStatus(status);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T reload(T customResource) {
        return (T) getNamespace(customResource.getMetadata().getNamespace()).getCustomResources(customResource.getClass())
                .get(customResource.getMetadata().getName());
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String namespace, String name) {
        Map<String, T> customResources = getNamespace(namespace).getCustomResources(clzz);
        return customResources.get(name);
    }

    @Override
    public void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase) {
        entandoCustomResource.getStatus().updateDeploymentPhase(phase, entandoCustomResource.getMetadata().getGeneration());
    }

    @Override
    public void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason) {
        entandoCustomResource.getStatus().findCurrentServerStatus()
                .orElseThrow(() -> new IllegalStateException("No server status recorded yet!"))
                .finishWith(new EntandoControllerFailureBuilder().withException(reason).build());
    }

    @Override
    public Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        NamespaceDouble namespace = getNamespace(resource);
        Optional<EntandoDatabaseService> first = namespace.getCustomResources(EntandoDatabaseService.class).values().stream()
                .filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms() == vendor).findFirst();
        return first.map(edb -> new ExternalDatabaseDeployment(namespace.getService(ExternalDatabaseDeployment.serviceName(edb)), edb));
    }

    @Override
    public KeycloakConnectionConfig findKeycloak(EntandoCustomResource resource, KeycloakPreference keycloakPreference) {
        Optional<ResourceReference> keycloakToUse = determineKeycloakToUse(resource, keycloakPreference);
        String secretName = keycloakToUse.map(KeycloakName::forTheAdminSecret)
                .orElse(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET);
        String configMapName = keycloakToUse.map(KeycloakName::forTheConnectionConfigMap)
                .orElse(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG);
        String configMapNamespace = keycloakToUse
                .map(resourceReference -> resourceReference.getNamespace().orElseThrow(IllegalStateException::new))
                .orElse(CONTROLLER_NAMESPACE);

        Secret secret = getNamespace(CONTROLLER_NAMESPACE).getSecret(secretName);
        ConfigMap configMap = getNamespace(configMapNamespace).getConfigMap(configMapName);
        if (secret == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak secret %s in namespace %s", secretName, CONTROLLER_NAMESPACE));
        }
        if (configMap == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak configMap %s in namespace %s", configMapName, configMapNamespace));
        }
        return new KeycloakConnectionConfig(secret, configMap);
    }

    @Override
    public Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoCustomResource peerInNamespace) {
        Collection<EntandoKeycloakServer> keycloakServers = getNamespace(peerInNamespace).getCustomResources(EntandoKeycloakServer.class)
                .values();
        if (keycloakServers.size() == 1) {
            return keycloakServers.stream().findAny();
        }
        return Optional.empty();
    }

    @Override
    public <T extends ClusterInfrastructureAwareSpec> Optional<InfrastructureConfig> findInfrastructureConfig(
            EntandoBaseCustomResource<T> resource) {
        Optional<ResourceReference> reference = determineClusterInfrastructureToUse(resource);
        return reference.map(rr -> new InfrastructureConfig(
                getNamespace(rr.getNamespace().orElseThrow(IllegalStateException::new))
                        .getConfigMap(InfrastructureConfig.connectionConfigMapNameFor(rr))));
    }

    @Override
    public ExposedService loadExposedService(EntandoCustomResource resource) {
        NamespaceDouble namespace = getNamespace(resource);
        Service service = namespace.getService(
                resource.getMetadata().getName() + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER + "-" + NameUtils.DEFAULT_SERVICE_SUFFIX);
        Ingress ingress = namespace.getIngress(
                resource.getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX);
        return new ExposedService(service, ingress);
    }

    @Override
    public DoneableConfigMap loadDefaultCapabilitiesConfigMap() {
        ConfigMap configMap = getNamespace(CONTROLLER_NAMESPACE)
                .getConfigMap(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME);
        if (configMap == null) {
            return new DoneableConfigMap(item -> {
                getNamespace(CONTROLLER_NAMESPACE).putConfigMap(item);
                return item;
            })
                    .withNewMetadata()
                    .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME)
                    .withNamespace(CONTROLLER_NAMESPACE)
                    .endMetadata()
                    .addToData(new HashMap<>());
        }
        return new DoneableConfigMap(configMap, item -> {
            getNamespace(CONTROLLER_NAMESPACE).putConfigMap(item);
            return item;
        });
    }

    @Override
    public ConfigMap loadDockerImageInfoConfigMap() {
        return getNamespace(EntandoOperatorConfig.getEntandoDockerImageInfoNamespace().orElse(CONTROLLER_NAMESPACE))
                .getConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap());

    }

    @Override
    public ConfigMap loadOperatorConfig() {
        return getNamespace(CONTROLLER_NAMESPACE).getConfigMap(KubeUtils.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME);
    }

    @Override
    public Optional<EntandoClusterInfrastructure> findClusterInfrastructureInNamespace(EntandoCustomResource resource) {
        return getNamespace(resource).getCustomResources(EntandoClusterInfrastructure.class).values().stream().findAny();
    }

}
