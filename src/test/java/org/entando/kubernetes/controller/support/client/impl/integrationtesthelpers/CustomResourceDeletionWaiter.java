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

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class CustomResourceDeletionWaiter {

    private final MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> operation;
    private String name;
    private String namespace;

    public CustomResourceDeletionWaiter(KubernetesClient client, String kind) {
        CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                .withPlural(kind.toLowerCase() + "s")
                .withVersion(TestFixturePreparation.CURRENT_ENTANDO_RESOURCE_VERSION)
                .withGroup("entando.org")
                .withScope("Namespaced")
                .withName(kind)
                .build();
        this.operation = client.genericKubernetesResources(context);
    }

    public CustomResourceDeletionWaiter named(String name) {
        this.name = name;
        return this;
    }

    public CustomResourceDeletionWaiter fromNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public void waitingAtMost(Duration duration) {
        waitingAtMost(duration.getSeconds(), TimeUnit.SECONDS);
    }

    public void waitingAtMost(long duration, TimeUnit timeUnit) {
        if (name == null) {
            // Delete all resources in the namespace
            if (!this.operation.inNamespace(namespace).list().getItems().isEmpty()) {
                this.operation.inNamespace(namespace).delete();
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> this.operation.inNamespace(namespace).list().getItems().isEmpty());
            }
        } else {
            // Delete a specific named resource
            if (this.operation.inNamespace(namespace).withName(name).get() != null) {
                this.operation.inNamespace(namespace).withName(name).delete();
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> this.operation.inNamespace(namespace).withName(name).get() == null);
            }
        }
    }
}
