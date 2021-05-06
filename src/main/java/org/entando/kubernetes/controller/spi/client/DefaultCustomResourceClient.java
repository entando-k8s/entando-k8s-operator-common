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

package org.entando.kubernetes.controller.spi.client;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public class DefaultCustomResourceClient implements CustomResourceClient {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");
    protected final KubernetesClient client;
    private final Map<String, CustomResourceDefinition> definitions = new ConcurrentHashMap<>();

    public DefaultCustomResourceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void prepareConfig() {
        EntandoOperatorConfigBase.setConfigMap(loadOperatorConfig());
        EntandoOperatorSpiConfig.getCertificateAuthoritySecretName()
                .ifPresent(s -> TrustStoreHelper
                        .trustCertificateAuthoritiesIn(client.secrets().inNamespace(client.getNamespace()).withName(s).fromServer().get()));

    }

    public ConfigMap loadOperatorConfig() {
        return client.configMaps().inNamespace(client.getNamespace())
                .withName(CustomResourceClient.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME).fromServer().get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        final MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operations = getOperations((Class<T>) r.getClass());
        if (operations.inNamespace(r.getMetadata().getNamespace()).withName(r.getMetadata().getName()).get() == null) {
            return operations.inNamespace(r.getMetadata().getNamespace()).create(r);
        } else {
            return operations.inNamespace(r.getMetadata().getNamespace()).withName(r.getMetadata().getName()).patch(r);
        }
    }

    public SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name) {
        try {
            final CustomResourceDefinitionContext context = resolveDefinitionContext(kind, apiVersion);
            final Map<String, Object> crMap = client.customResource(context).get(namespace, name);
            final ObjectMapper objectMapper = new ObjectMapper();
            final SerializedEntandoResource serializedEntandoResource = objectMapper
                    .readValue(objectMapper.writeValueAsString(crMap), SerializedEntandoResource.class);
            serializedEntandoResource.setDefinition(context);
            return serializedEntandoResource;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public HasMetadata loadStandardResource(String kind, String namespace, String name) {
        return SupportedStandardResourceKind.resolveFromKind(kind)
                .map(k -> k.getOperation(client)
                        .inNamespace(namespace)
                        .withName(name)
                        .fromServer()
                        .get())
                .orElseThrow(() -> new IllegalStateException(
                        "Resource kind '" + kind + "' not supported."));
    }

    protected Supplier<IllegalStateException> notFound(String kind, String namespace, String name) {
        return () -> new IllegalStateException(format("Could not find the %s '%s' in the namespace %s", kind, name, namespace));
    }

    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return ofNullable(getOperations(clzz).inNamespace(resourceNamespace)
                .withName(resourceName).get()).orElseThrow(() -> notFound(clzz.getSimpleName(), resourceNamespace, resourceName).get());
    }

    @SuppressWarnings("unchecked")
    protected <T extends EntandoCustomResource> MixedOperation<T, KubernetesResourceList<T>, Resource<T>> getOperations(Class<T> c) {
        return client.customResources((Class) c);
    }

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

    @SuppressWarnings({"unchecked"})
    //These casts are necessary to circumvent our "inaccurate" use of type parameters for our generic Serializable resources
    //We have to use the deprecated methods in question to "generically" resolve our Serializable resources
    //TODO find a way to serialize to our generic objects using the HashMaps from the Raw custom resources
    public <T extends EntandoCustomResource> T reload(T customResource) {
        if (customResource instanceof SerializedEntandoResource) {
            return (T) loadCustomResource(
                    customResource.getApiVersion(),
                    customResource.getKind(),
                    customResource.getMetadata().getNamespace(),
                    customResource.getMetadata().getName());
        } else {
            return (T) load(customResource.getClass(), customResource.getMetadata().getNamespace(), customResource.getMetadata().getName());
        }
    }

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

    @SuppressWarnings({"unchecked", "rawtypes", "java:S1905", "java:S1874"})
    //These casts are necessary to circumvent our "inaccurate" use of type parameters for our generic Serializable resources
    //We have to use the deprecated methods in question to "generically" resolve our Serializable resources
    //TODO find a way to serialize to our generic objects using the HashMaps from the Raw custom resources
    private <T extends EntandoCustomResource> void performStatusUpdate(T customResource,
            Consumer<T> consumer, UnaryOperator<EventBuilder> eventPopulator) {
        final EventBuilder doneableEvent = new EventBuilder()
                .withNewMetadata()
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName() + "-" + NameUtils.randomNumeric(8))
                .withOwnerReferences(ResourceUtils.buildOwnerReference(customResource))
                .endMetadata()
                .withCount(1)
                .withFirstTimestamp(dateTimeFormatter.format(LocalDateTime.now()))
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
        client.v1().events().inNamespace(customResource.getMetadata().getNamespace()).create(eventPopulator.apply(doneableEvent).build());
        T latest;
        if (customResource instanceof SerializedEntandoResource) {
            try {
                SerializedEntandoResource ser = (SerializedEntandoResource) customResource;
                CustomResourceDefinitionContext definition = Optional.ofNullable(ser.getDefinition()).orElse(
                        resolveDefinitionContext(ser.getKind(), ser.getApiVersion()));
                ser.setDefinition(definition);
                RawCustomResourceOperationsImpl resource = client.customResource(definition)
                        .inNamespace(customResource.getMetadata().getNamespace())
                        .withName(customResource.getMetadata().getName());
                final ObjectMapper objectMapper = new ObjectMapper();
                ser = objectMapper.readValue(objectMapper.writeValueAsString(resource.get()), SerializedEntandoResource.class);
                ser.setDefinition(definition);
                latest = (T) ser;
                consumer.accept(latest);
                resource.updateStatus(objectMapper.writeValueAsString(latest));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operations = getOperations(
                    (Class<T>) customResource.getClass());
            Resource<T> resource = operations
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName());
            latest = resource.fromServer().get();
            consumer.accept(latest);
            resource.updateStatus(latest);
        }
    }

    private CustomResourceDefinitionContext resolveDefinitionContext(String kind, String apiVersion) {
        final String key = apiVersion + "/" + kind;
        return CustomResourceDefinitionContext.fromCrd(
                definitions.computeIfAbsent(key, s -> client.apiextensions().v1beta1().customResourceDefinitions()
                        .withLabel(CRD_KIND_LABEL_NAME, kind)
                        .list().getItems()
                        .stream().filter(crd ->
                                crd.getSpec().getNames().getKind().equals(kind) && apiVersion
                                        .startsWith(crd.getSpec().getGroup())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Could not find CRD for " + kind))));
    }
}
