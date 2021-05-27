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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.client.ExecutionResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.client.impl.SupportedStandardResourceKind;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.support.client.ConfigMapBasedSsoConnectionInfo;
import org.entando.kubernetes.controller.support.client.DoneableConfigMap;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.InternalServerStatus;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.test.common.DatabaseDeploymentResult;

public class EntandoResourceClientDouble extends AbstractK8SClientDouble implements EntandoResourceClient {

    private ConfigMap crdNameMap;

    public EntandoResourceClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
        final ConfigMapBuilder builder = new ConfigMapBuilder(
                Objects.requireNonNullElseGet(getNamespace(CONTROLLER_NAMESPACE).getConfigMap(ENTANDO_CRD_NAMES_CONFIG_MAP), ConfigMap::new)
        );
        crdNameMap = getCluster().getResourceProcessor()
                .processResource(getNamespace(CONTROLLER_NAMESPACE).getConfigMaps(), builder.withNewMetadata()
                        .withName(ENTANDO_CRD_NAMES_CONFIG_MAP)
                        .withNamespace(CONTROLLER_NAMESPACE).endMetadata().build());

    }

    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        if (r != null) {
            this.getCluster().getResourceProcessor().processResource(getNamespace(r).getCustomResources(r.getKind()), r);
        }
        return r;
    }

    @Override
    public ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException {
        if (pod != null) {
            PodResource<Pod> podResource = new PodResourceDouble();
            return executeAndWait(podResource, containerName, timeoutSeconds, commands);
        }
        return null;
    }

    public void putEntandoDatabaseService(EntandoDatabaseService externalDatabase) {
        createOrPatchEntandoResource(externalDatabase);
    }

    @Override
    public String getNamespace() {
        return CONTROLLER_NAMESPACE;
    }

    @Override
    public void prepareConfig() {
        EntandoOperatorConfigBase.setConfigMap(loadOperatorConfig());
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

    @SuppressWarnings("unchecked")
    @Override
    public <T extends EntandoCustomResource> T updateStatus(T customResource, AbstractServerStatus status) {
        final T found = (T) getNamespace(customResource).getCustomResources(customResource.getKind())
                .get(customResource.getMetadata().getName());
        found.getStatus().putServerStatus(status);
        return getCluster().getResourceProcessor().processResource(getNamespace(found).getCustomResources(found.getKind()), found);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends EntandoCustomResource> T updatePhase(T customResource, EntandoDeploymentPhase phase) {
        final T found = (T) getNamespace(customResource).getCustomResources(customResource.getKind())
                .get(customResource.getMetadata().getName());
        found.getStatus().updateDeploymentPhase(phase, found.getMetadata().getGeneration());
        return getCluster().getResourceProcessor()
                .processResource(getNamespace(found).getCustomResources(found.getKind()), found);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends EntandoCustomResource> T deploymentFailed(T customResource, Exception reason) {
        final T found = (T) getNamespace(customResource).getCustomResources(customResource.getKind())
                .get(customResource.getMetadata().getName());
        if (found.getStatus().findCurrentServerStatus().isEmpty()) {
            found.getStatus().putServerStatus(new InternalServerStatus(NameUtils.MAIN_QUALIFIER));
        }
        found.getStatus().findCurrentServerStatus()
                .ifPresent(abstractServerStatus -> abstractServerStatus
                        .finishWith(new EntandoControllerFailureBuilder().withException(reason).build()));
        found.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.FAILED, found.getMetadata().getGeneration());
        return getCluster().getResourceProcessor().processResource(getNamespace(customResource).getCustomResources(found.getKind()), found);
    }

    @Override
    public Optional<DatabaseConnectionInfo> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        NamespaceDouble namespace = getNamespace(resource);
        Optional<EntandoDatabaseService> first = namespace.getCustomResources(EntandoDatabaseService.class).values().stream()
                .filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms().orElse(DbmsVendor.POSTGRESQL) == vendor)
                .findFirst();
        return first.map(edb -> new DatabaseDeploymentResult(namespace.getService(NameUtils.standardServiceName(edb)), edb));
    }

    @Override
    public SsoConnectionInfo findKeycloak(EntandoCustomResource resource, KeycloakPreference keycloakPreference) {
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
        return new ConfigMapBasedSsoConnectionInfo(secret, configMap);
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
        return getNamespace(CONTROLLER_NAMESPACE).getConfigMap(KubernetesClientForControllers.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME);
    }

    @Override
    public SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final SerializedEntandoResource resource = objectMapper
                    .readValue(objectMapper.writeValueAsString(getNamespace(namespace).getCustomResources(kind).get(name)),
                            SerializedEntandoResource.class);
            final String group = kind + "." + apiVersion.substring(0, apiVersion.indexOf("/"));
            resource.setDefinition(CustomResourceDefinitionContext.fromCrd(getCluster()
                    .getCustomResourceDefinition(crdNameMap.getData().get(group))));
            return resource;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public HasMetadata loadStandardResource(String kind, String namespace, String name) {
        switch (SupportedStandardResourceKind.resolveFromKind(kind).get()) {
            case DEPLOYMENT:
                return getNamespace(namespace).getDeployment(name);
            case INGRESS:
                return getNamespace(namespace).getIngress(name);
            case SERVICE:
                return getNamespace(namespace).getService(name);
            case SECRET:
                return getNamespace(namespace).getSecret(name);
            case POD:
                return getNamespace(namespace).getPod(name);
            case PERSISTENT_VOLUME_CLAIM:
                return getNamespace(namespace).getPersistentVolumeClaim(name);
            default:
                return null;
        }

    }

    @Override
    public List<Event> listEventsFor(EntandoCustomResource resource) {
        return Collections.emptyList();
    }

    public void registerCustomResourceDefinition(String resourceName) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        final CustomResourceDefinition crd = objectMapper
                .readValue(Thread.currentThread().getContextClassLoader().getResource(resourceName),
                        CustomResourceDefinition.class);
        getCluster().putCustomResourceDefinition(crd);
        crdNameMap = getCluster().getResourceProcessor().processResource(getNamespace(CONTROLLER_NAMESPACE).getConfigMaps(),
                new ConfigMapBuilder(getNamespace(CONTROLLER_NAMESPACE).getConfigMap(ENTANDO_CRD_NAMES_CONFIG_MAP))
                        .addToData(crd.getSpec().getNames().getKind() + "." + crd.getSpec().getGroup(), crd.getMetadata().getName())
                        .build());

    }

}
