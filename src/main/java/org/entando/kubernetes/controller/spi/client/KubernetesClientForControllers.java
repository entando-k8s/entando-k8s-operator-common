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

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public interface KubernetesClientForControllers {

    String ENTANDO_CRD_NAMES_CONFIG_MAP = "entando-crd-names";
    String ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME = "entando-operator-config";

    void prepareConfig();

    String getNamespace();

    Service loadControllerService(String name);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T updateStatus(T customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T updatePhase(T customResource, EntandoDeploymentPhase phase);

    <T extends EntandoCustomResource> T deploymentFailed(T entandoCustomResource, Exception reason);

    default EntandoCustomResource resolveCustomResourceToProcess(Collection<Class<? extends EntandoCustomResource>> supportedTypes) {
        String resourceName = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME);
        String resourceNamespace = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE);
        String kind = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND);
        return load(supportedTypes.stream().filter(c -> c.getSimpleName().equals(kind)).findAny().orElseThrow(() ->
                        new IllegalArgumentException(format("The resourceKind %s was not found in the list of supported types", kind))),
                resourceNamespace, resourceName);

    }

    private String resolveProperty(EntandoOperatorSpiConfigProperty name) {
        return EntandoOperatorConfigBase.lookupProperty(name)
                .orElseThrow(() -> new IllegalStateException(
                        format("No %s specified. Please set either the Environment Variable %s or the System Property %s", name
                                .getCamelCaseName(), name.name(), name.getJvmSystemProperty())));
    }

    SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name);

    HasMetadata loadStandardResource(String kind, String namespace, String name);

    @SuppressWarnings({"java:S106"})
    default ExecutionResult executeAndWait(PodResource<Pod> podResource, String containerName, int timeoutSeconds,
            String... script) throws TimeoutException {
        StringBuilder sb = new StringBuilder();
        for (String s : script) {
            sb.append(s);
            sb.append(" || exit $?\n");
        }
        sb.append("exit $?\n");
        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        try {
            final CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
            ExecutionResult listener = new ExecutionResult(future);
            podResource.inContainer(containerName)
                    .readingInput(in)
                    .writingOutput(listener.getOutput())
                    .writingError(listener.getOutput())
                    .writingErrorChannel(listener.getErrorChannel())
                    .withTTY()
                    .usingListener(listener).exec();
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    List<Event> listEventsFor(EntandoCustomResource resource);

    ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException;
}
