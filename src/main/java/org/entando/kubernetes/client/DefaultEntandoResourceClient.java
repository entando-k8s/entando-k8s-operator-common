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

package org.entando.kubernetes.client;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneableEvent;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.ClusterInfrastructureAwareSpec;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoResourceOperationsRegistry;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class DefaultEntandoResourceClient implements EntandoResourceClient, PatchableClient {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");
    private final KubernetesClient client;
    private final EntandoResourceOperationsRegistry entandoResourceRegistry;
    private final Map<String, CustomResourceDefinition> definitions = new ConcurrentHashMap<>();

    public DefaultEntandoResourceClient(KubernetesClient client) {
        this.client = client;
        entandoResourceRegistry = new EntandoResourceOperationsRegistry(client);
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
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
                .orElse(client.getNamespace());
        //This secret is duplicated in the deployment namespace, but the controller can only read the one in its own namespace
        Secret secret = this.client.secrets().withName(secretName).fromServer().get();
        if (secret == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak secret %s in namespace %s", secretName, client.getNamespace()));
        }
        //The configmap comes from the deployment namespace, unless it is a pre-configured keycloak
        ConfigMap configMap = this.client.configMaps().inNamespace(configMapNamespace).withName(configMapName).fromServer().get();
        if (configMap == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak ConfigMap %s in namespace %s", configMapName, configMapNamespace));
        }
        return new KeycloakConnectionConfig(secret, configMap);

    }

    @Override
    public Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoCustomResource peerInNamespace) {
        List<EntandoKeycloakServer> items = entandoResourceRegistry.getOperations(EntandoKeycloakServer.class)
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).list().getItems();
        if (items.size() == 1) {
            return Optional.of(items.get(0));
        }
        return Optional.empty();
    }

    @Override
    public DoneableConfigMap loadDefaultConfigMap() {
        Resource<ConfigMap, DoneableConfigMap> resource = client.configMaps().inNamespace(client.getNamespace())
                .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CONFIGMAP_NAME);
        if (resource.get() == null) {
            return client.configMaps().inNamespace(client.getNamespace()).createNew()
                    .withNewMetadata()
                    .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CONFIGMAP_NAME)
                    .withNamespace(client.getNamespace())
                    .endMetadata()
                    .addToData(new HashMap<>());

        }
        return resource.edit().editMetadata()
                //to ensure there is a state change so that the patch request does not get rejected
                .addToAnnotations(KubeUtils.UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
                .endMetadata();
    }

    @Override
    public Optional<EntandoClusterInfrastructure> findClusterInfrastructureInNamespace(EntandoCustomResource resource) {
        List<EntandoClusterInfrastructure> items = entandoResourceRegistry
                .getOperations(EntandoClusterInfrastructure.class)
                .inNamespace(resource.getMetadata().getNamespace()).list().getItems();
        if (items.size() == 1) {
            return Optional.of(items.get(0));
        }
        return Optional.empty();
    }

    @Override
    public <T extends ClusterInfrastructureAwareSpec> Optional<InfrastructureConfig> findInfrastructureConfig(
            EntandoBaseCustomResource<T> resource) {
        Optional<ResourceReference> clusterInfrastructureToUse = determineClusterInfrastructureToUse(resource);
        return clusterInfrastructureToUse.map(resourceReference -> new InfrastructureConfig(
                this.client.configMaps()
                        .inNamespace(resourceReference.getNamespace().orElseThrow(IllegalStateException::new))
                        .withName(InfrastructureConfig.connectionConfigMapNameFor(resourceReference))
                        .get()));
    }

    @Override
    public ExposedService loadExposedService(EntandoCustomResource resource) {
        return new ExposedService(
                loadService(resource, NameUtils.standardServiceName(resource)),
                loadIngress(resource, NameUtils.standardIngressName(resource)));
    }

    @Override
    public Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        List<EntandoDatabaseService> externalDatabaseList = getOperations(EntandoDatabaseService.class)
                .inNamespace(resource.getMetadata().getNamespace()).list().getItems();
        return externalDatabaseList.stream().filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms() == vendor)
                .findFirst().map(externalDatabase ->
                        new ExternalDatabaseDeployment(
                                loadService(externalDatabase, ExternalDatabaseDeployment.serviceName(externalDatabase)),
                                externalDatabase));
    }

    protected Supplier<IllegalStateException> notFound(String kind, String namespace, String name) {
        return () -> new IllegalStateException(format("Could not find the %s '%s' in the namespace %s", kind, name, namespace));
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return ofNullable(getOperations(clzz).inNamespace(resourceNamespace)
                .withName(resourceName).get()).orElseThrow(() -> notFound(clzz.getSimpleName(), resourceNamespace, resourceName).get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        Class<T> type = (Class<T>) r.getClass();
        return createOrPatch(r, r, this.getOperations(type));

    }

    private <T extends EntandoCustomResource,
            D extends DoneableEntandoCustomResource<T, D>> CustomResourceOperationsImpl<T, CustomResourceList<T>, D> getOperations(
            Class<T> c) {
        return entandoResourceRegistry.getOperations(c);
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status) {
        performStatusUpdate(customResource,
                t -> t.getStatus().putServerStatus(status),
                e -> e.withType("Normal")
                        .withReason("StatusUpdate")
                        .withMessage(format("The %s  %s/%s received status update %s/%s ",
                                customResource.getKind(),
                                customResource.getMetadata().getNamespace(),
                                customResource.getMetadata().getName(),
                                status.getType(),
                                status.getQualifier()))
                        .withAction("STATUS_CHANGE")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T reload(T customResource) {
        if (customResource instanceof EntandoBaseCustomResource) {
            return (T) load(customResource.getClass(), customResource.getMetadata().getNamespace(), customResource.getMetadata().getName());
        } else {
            return (T) client.customResources(
                    resolveDefinition((SerializedEntandoResource) customResource),
                    SerializedEntandoResource.class,
                    CustomResourceList.class,
                    DoneableCustomResource.class
            )
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName())
                    .fromServer()
                    .get();
        }
    }

    @Override
    public void updatePhase(EntandoCustomResource customResource, EntandoDeploymentPhase phase) {
        performStatusUpdate(customResource,
                t -> t.getStatus().updateDeploymentPhase(phase, t.getMetadata().getGeneration()),
                e -> e.withType("Normal")
                        .withReason("PhaseUpdated")
                        .withMessage(format("The deployment of %s  %s/%s was updated  to %s",
                                customResource.getKind(),
                                customResource.getMetadata().getNamespace(),
                                customResource.getMetadata().getName(),
                                phase.name()))
                        .withAction("PHASE_CHANGE")
        );
    }

    @Override
    public void deploymentFailed(EntandoCustomResource customResource, Exception reason) {
        performStatusUpdate(customResource,
                t -> {
                    t.getStatus().findCurrentServerStatus()
                            .ifPresent(
                                    newStatus -> newStatus.finishWith(new EntandoControllerFailureBuilder()
                                            .withException(reason)
                                            .withFailedObjectName(customResource.getMetadata().getNamespace(),
                                                    customResource.getMetadata().getName())
                                            .withFailedObjectType(customResource.getKind())
                                            .build()));
                    t.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.FAILED, t.getMetadata().getGeneration());
                },
                e -> e.withType("Error")
                        .withReason("Failed")
                        .withMessage(
                                format("The deployment of %s %s/%s failed due to %s. Fix the root cause and then trigger a redeployment "
                                                + "by adding the annotation 'entando.org/processing-instruction: force'",
                                        customResource.getKind(),
                                        customResource.getMetadata().getNamespace(),
                                        customResource.getMetadata().getName(),
                                        reason.getMessage()))
                        .withAction("FAILED")
        );
    }

    private Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    private Ingress loadIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    protected Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    @SuppressWarnings("unchecked")
    private <T extends EntandoCustomResource> void performStatusUpdate(EntandoCustomResource customResource,
            Consumer<T> consumer, UnaryOperator<DoneableEvent> eventPopulator) {
        final DoneableEvent doneableEvent = client.events().inNamespace(customResource.getMetadata().getNamespace()).createNew()
                .withNewMetadata()
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName() + "-" + NameUtils.randomNumeric(4))
                .withOwnerReferences(ResourceUtils.buildOwnerReference(customResource))
                .endMetadata()
                .withCount(1)
                .withLastTimestamp(dateTimeFormatter.format(LocalDateTime.now()))
                .withNewSource(NameUtils.controllerNameOf(customResource), null)
                .withNewInvolvedObject()
                .withKind(customResource.getKind())
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .withUid(customResource.getMetadata().getUid())
                .withResourceVersion(customResource.getMetadata().getResourceVersion())
                .withApiVersion(customResource.getApiVersion())
                .withFieldPath("status")
                .endInvolvedObject();
        eventPopulator.apply(doneableEvent).done();
        final CustomResourceOperationsImpl<T, CustomResourceList<T>, ?> operations;
        if (customResource instanceof EntandoBaseCustomResource) {
            operations = getOperations((Class<T>) customResource.getClass());
        } else {
            SerializedEntandoResource ser = (SerializedEntandoResource) customResource;
            if (ser.getDefinition() == null) {
                ser.setDefinition(resolveDefinition(ser));
            }
            operations = (CustomResourceOperationsImpl) client
                    .customResources(ser.getDefinition(), SerializedEntandoResource.class, CustomResourceList.class,
                            DoneableCustomResource.class);
        }

        Resource<T, ?> resource = operations
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName());
        T latest = resource.fromServer().get();
        consumer.accept(latest);
        resource.updateStatus(latest);
    }

    @SuppressWarnings("rawtypes")
    private CustomResourceDefinition resolveDefinition(SerializedEntandoResource ser) {
        final String key = ser.getApiVersion() + "/" + ser.getKind();
        CustomResourceDefinition definition = definitions.get(key);
        if (definition == null) {
            definition = client.customResourceDefinitions().list().getItems()
                    .stream().filter(crd ->
                            crd.getSpec().getNames().getKind().equals(ser.getKind()) && ser
                                    .getApiVersion()
                                    .startsWith(crd.getSpec().getGroup())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find CRD for " + ser.getKind()));
            definitions.put(key, definition);
        }
        return definition;
    }

}
