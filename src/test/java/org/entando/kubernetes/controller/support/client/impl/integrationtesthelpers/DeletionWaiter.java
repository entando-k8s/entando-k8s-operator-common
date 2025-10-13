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

package org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers;

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeletionWaiter<
        R extends HasMetadata,
        L extends KubernetesResourceList<R>,
        O extends Resource<R>> {

    private static final Logger LOGGER = Logger.getLogger(DeletionWaiter.class.getName());
    private final MixedOperation<R, L, O> operation;
    private String name;
    private String namespace;
    private final Map<String, String> labels = new HashMap<>();

    public DeletionWaiter(MixedOperation<R, L, O> operation) {
        this.operation = operation;
    }

    public static <R extends HasMetadata,
            L extends KubernetesResourceList<R>,
            O extends Resource<R>> DeletionWaiter<R, L, O> delete(MixedOperation<R, L, O> operation) {
        return new DeletionWaiter<>(operation);
    }

    public DeletionWaiter<R, L, O> named(String name) {
        this.name = name;
        return this;
    }

    public DeletionWaiter<R, L, O> withLabel(String labelName, String labelValue) {
        labels.put(labelName, labelValue);
        return this;
    }

    public DeletionWaiter<R, L, O> withLabel(String labelName) {
        labels.put(labelName, null);
        return this;
    }

    public DeletionWaiter<R, L, O> fromNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public void waitingAtMost(Duration duration) {
        waitingAtMost(duration.getSeconds(), TimeUnit.SECONDS);
    }

    public void waitingAtMost(long duration, TimeUnit timeUnit) {
        if (name == null) {
            if (labels.isEmpty()) {
                await().atMost(duration, timeUnit).ignoreExceptions().until(() -> {
                    this.operation.inNamespace(namespace).delete();
                    return this.operation.inNamespace(namespace).list().getItems().isEmpty();

                });
            } else {
                await().atMost(duration, timeUnit).ignoreExceptions().until(() -> {
                    deleteIndividually(duration, timeUnit, this.operation.inNamespace(namespace).withLabels(labels).list());
                    return this.operation.inNamespace(namespace).withLabels(labels).list().getItems().isEmpty();

                });
            }
        } else {
            deleteSingleItem(duration, timeUnit);
        }
    }

    protected void deleteSingleItem(long duration, TimeUnit timeUnit) {
        await().atMost(duration, timeUnit)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        // Scale to zero if the resource supports it (e.g., Deployments, StatefulSets)
                        try {
                            O resource = this.operation.inNamespace(namespace).withName(name);
                            // Try to scale to 0 using reflection to handle scalable resources
                            resource.getClass().getMethod("scale", int.class, boolean.class).invoke(resource, 0, true);
                            LOGGER.log(Level.WARNING,
                                    (format("Scaled %s  %s/%s to zero", ((OperationSupport) operation).getResourceT(), namespace, name)));
                        } catch (NoSuchMethodException e) {
                            // Resource doesn't support scaling, skip
                        }
                        LOGGER.log(Level.WARNING,
                                (format("Deleting %s  %s/%s", ((OperationSupport) operation).getResourceT(), namespace, name)));
                        this.operation.inNamespace(namespace).withName(name).withGracePeriod(0).delete();
                    } catch (KubernetesClientException e) {
                        LOGGER.log(Level.WARNING, format("Deletion of %s/%s failed.", namespace, name), e);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, format("Error during deletion of %s/%s.", namespace, name), e);
                    }
                    return this.operation.inNamespace(namespace).withName(name).get() == null;
                });
    }

    protected void deleteIndividually(long duration, TimeUnit timeUnit, L list) {
        for (R item : list.getItems()) {
            name = item.getMetadata().getName();
            deleteSingleItem(duration, timeUnit);
        }
    }

}
