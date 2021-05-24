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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public interface KubernetesClientForControllers {

    String ENTANDO_CRD_NAMES_CONFIG_MAP = "entando-crd-names";
    String ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME = "entando-operator-config";
    AtomicBoolean ENQUEUE_POD_WATCH_HOLDERS = new AtomicBoolean(false);

    void prepareConfig();

    String getNamespace();

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
    default EntandoExecListener executeAndWait(PodResource<Pod> podResource, String containerName, int timeoutSeconds,
            String... script) {
        StringBuilder sb = new StringBuilder();
        for (String s : script) {
            sb.append(s);
            sb.append('\n');
        }
        sb.append("exit 0\n");
        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        try {
            Object mutex = new Object();
            synchronized (mutex) {
                EntandoExecListener listener = new EntandoExecListener(mutex, timeoutSeconds);
                if (ENQUEUE_POD_WATCH_HOLDERS.get()) {
                    getExecListenerHolder().add(listener);//because it should never be full during tests. fail early.
                }
                final Execable<String, ExecWatch> execable = podResource.inContainer(containerName)
                        .readingInput(in)
                        .writingOutput(listener.getOutWriter())
                        .redirectingError()
                        .withTTY()
                        .usingListener(listener);
                listener.setExecable(execable);
                execable.exec();
                while (listener.shouldStillWait()) {
                    mutex.wait(1000);
                }
                if (listener.hasFailed()) {
                    throw new IllegalStateException(format("Command did not meet the wait condition within 20 seconds: %s", sb.toString()));
                }
                return listener;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    EntandoExecListener executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands);

    /**
     * A getter for the an AtomicReference to the most recently constructed ExecListener for testing purposes.
     */
    BlockingQueue<EntandoExecListener> getExecListenerHolder();
}
