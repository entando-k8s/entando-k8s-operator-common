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

package org.entando.kubernetes.controller.spi.client.impl;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.retry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.ExecutionResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.InternalServerStatus;

public class DefaultKubernetesClientForControllers implements KubernetesClientForControllers {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");
    protected final KubernetesClient client;
    private ConfigMap crdNameMap;

    public DefaultKubernetesClientForControllers(KubernetesClient client) {
        this.client = client;

    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public Service loadControllerService(String name) {
        return client.services().inNamespace(client.getNamespace()).withName(name).get();
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
                .withName(KubernetesClientForControllers.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME).fromServer().get();
    }

    @Override
    public ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException {
        PodResource<Pod> podResource = this.client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return executeAndWait(podResource, containerName, timeoutSeconds, commands);
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
        return ioSafe(() -> {

            final CustomResourceDefinitionContext context = resolveDefinitionContext(kind, apiVersion);
            final Map<String, Object> crMap = client.customResource(context).get(namespace, name);
            final ObjectMapper objectMapper = new ObjectMapper();
            final SerializedEntandoResource serializedEntandoResource = objectMapper
                    .readValue(objectMapper.writeValueAsString(crMap), SerializedEntandoResource.class);
            serializedEntandoResource.setDefinition(context);
            return serializedEntandoResource;
        });
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

    @Override
    public List<Event> listEventsFor(EntandoCustomResource resource) {
        return client.v1().events().inAnyNamespace().withLabels(ResourceUtils.labelsFromResource(resource)).list().getItems();
    }

    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return getOperations(clzz).inNamespace(resourceNamespace).withName(resourceName).fromServer().get();
    }

    @SuppressWarnings("unchecked")
    protected <T extends EntandoCustomResource> MixedOperation<T, KubernetesResourceList<T>, Resource<T>> getOperations(Class<T> c) {
        return client.customResources((Class) c);
    }

    @Override
    public <T extends EntandoCustomResource> T updateStatus(T customResource, AbstractServerStatus status) {
        return issueEventAndPerformStatusUpdate(customResource,
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

    @Override
    public <T extends EntandoCustomResource> T updatePhase(T customResource, EntandoDeploymentPhase phase) {
        return issueEventAndPerformStatusUpdate(customResource,
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

    public <T extends EntandoCustomResource> T deploymentFailed(T customResource, Exception reason, String serverQualifier) {
        return issueEventAndPerformStatusUpdate(customResource,
                t -> {
                    String qualifierToUse = ofNullable(serverQualifier).orElse(NameUtils.MAIN_QUALIFIER);
                    if (t.getStatus().getServerStatus(qualifierToUse).isEmpty()) {
                        t.getStatus().putServerStatus(new InternalServerStatus(qualifierToUse));
                    }
                    t.getStatus().getServerStatus(qualifierToUse)
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

    private <T extends EntandoCustomResource> T issueEventAndPerformStatusUpdate(T customResource, Consumer<T> consumer,
            UnaryOperator<EventBuilder> eventPopulator) {
        issueEvent(customResource, eventPopulator);
        //Could be updating the status concurrently from multiple Deployables
        return retry(() -> performStatusUpdate(customResource, consumer),
                e -> e instanceof KubernetesClientException && ((KubernetesClientException) e).getCode() == HttpURLConnection.HTTP_CONFLICT,
                4);
    }

    private <T extends EntandoCustomResource> void issueEvent(T customResource, UnaryOperator<EventBuilder> eventPopulator) {
        final EventBuilder doneableEvent = new EventBuilder()
                .withNewMetadata()
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName() + "-" + NameUtils.randomNumeric(8))
                .withLabels(ResourceUtils.labelsFromResource(customResource))
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
    }

    @SuppressWarnings({"unchecked", "java:S1905", "java:S1874"})
    //These casts are necessary to circumvent our "inaccurate" use of type parameters for our generic Serializable resources
    //We have to use the deprecated methods in question to "generically" resolve our Serializable resources
    private <T extends EntandoCustomResource> T performStatusUpdate(T customResource, Consumer<T> consumer) {
        if (customResource instanceof SerializedEntandoResource) {
            return ioSafe(() -> {
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
                T latest = (T) ser;
                consumer.accept(latest);
                return (T) objectMapper.readValue(
                        objectMapper.writeValueAsString(resource.updateStatus(objectMapper.writeValueAsString(latest))),
                        SerializedEntandoResource.class);
            });
        } else {
            MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operations = getOperations(
                    (Class<T>) customResource.getClass());
            Resource<T> resource = operations
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName());
            T latest = resource.fromServer().get();
            consumer.accept(latest);
            return resource.updateStatus(latest);
        }
    }

    private CustomResourceDefinitionContext resolveDefinitionContext(String kind, String apiVersion) {
        final String key = kind + "." + apiVersion.substring(0, apiVersion.indexOf("/"));
        final String name = getCrdNameMap().getData().get(key);
        return CustomResourceDefinitionContext.fromCrd(client.apiextensions().v1beta1().customResourceDefinitions()
                .withName(name).get());
    }

    protected ConfigMap getCrdNameMap() {
        crdNameMap = Objects.requireNonNullElseGet(crdNameMap,
                () -> Objects.requireNonNullElseGet(
                        client.configMaps().inNamespace(client.getNamespace()).withName(ENTANDO_CRD_NAMES_CONFIG_MAP).get(),
                        () -> client.configMaps().inNamespace(client.getNamespace())
                                .create(new ConfigMapBuilder()
                                        .withNewMetadata()
                                        .withNamespace(client.getNamespace())
                                        .withName(ENTANDO_CRD_NAMES_CONFIG_MAP)
                                        .endMetadata()
                                        .addToData(client.apiextensions().v1beta1().customResourceDefinitions()
                                                .withLabel(LabelNames.CRD_OF_INTEREST.getName())
                                                .list()
                                                .getItems()
                                                .stream()
                                                .collect(Collectors
                                                        .toMap(crd -> crd.getSpec().getNames().getKind() + "." + crd.getSpec()
                                                                        .getGroup(),
                                                                crd -> crd.getMetadata().getName())))
                                        .build())));

        return crdNameMap;
    }
}
