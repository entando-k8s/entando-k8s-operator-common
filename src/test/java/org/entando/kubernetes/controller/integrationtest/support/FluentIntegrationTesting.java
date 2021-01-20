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

package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.entando.kubernetes.controller.integrationtest.support.TestFixtureRequest.DeletionRequestBuilder;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public interface FluentIntegrationTesting extends FluentTraversals {

    TimeUnit SECONDS = TimeUnit.SECONDS;
    TimeUnit MINUTES = TimeUnit.MINUTES;

    default DeletionRequestBuilder deleteAll(Class<? extends EntandoBaseCustomResource<?>> type) {
        return new TestFixtureRequest().deleteAll(type);
    }

    default ConditionFactory await() {
        return Awaitility.await().ignoreExceptions();
    }

    default <R extends HasMetadata,
            L extends KubernetesResourceList<R>,
            D extends Doneable<R>,
            O extends Resource<R, D>> DeletionWaiter<R, L, D, O> delete(MixedOperation<R, L, D, O> operation) {
        return new DeletionWaiter<>(operation);
    }
}
