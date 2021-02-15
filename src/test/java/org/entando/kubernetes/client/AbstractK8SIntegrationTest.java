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

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.InterProcessTestData;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.integrationtest.common.DeletionWaiter;
import org.entando.kubernetes.test.integrationtest.common.EntandoOperatorTestConfig;
import org.entando.kubernetes.test.integrationtest.common.TestFixturePreparation;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractK8SIntegrationTest implements FluentTraversals, InterProcessTestData, PodBehavior {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private DefaultSimpleK8SClient defaultSimpleK8SClient;
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private KubernetesClient fabric8Client;

    protected <R extends HasMetadata,
            L extends KubernetesResourceList<R>,
            D extends Doneable<R>,
            O extends Resource<R, D>> void deleteAll(MixedOperation<R, L, D, O> operation) {
        for (String s : getNamespacesToUse()) {
            new DeletionWaiter<>(operation).fromNamespace(s).waitingAtMost(100, TimeUnit.SECONDS);
        }
    }

    @Override
    public SimpleK8SClient<?> getClient() {
        return getSimpleK8SClient();
    }

    @AfterEach
    void teardown() {
        scheduler.shutdownNow();
    }

    protected abstract String[] getNamespacesToUse();

    protected DefaultSimpleK8SClient getSimpleK8SClient() {
        return defaultSimpleK8SClient;
    }

    protected KubernetesClient getFabric8Client() {
        return this.fabric8Client;
    }

    @BeforeEach
    public void setup() {
        if (EntandoOperatorTestConfig.emulateKubernetes()) {
            PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
            fabric8Client = server.getClient();
        } else {
            PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
            fabric8Client = new DefaultKubernetesClient();
            Arrays.stream(getNamespacesToUse())
                    .filter(s -> fabric8Client.namespaces().withName(s).get() == null)
                    .forEach(s -> TestFixturePreparation.createNamespace(fabric8Client, s));
        }
        defaultSimpleK8SClient = new DefaultSimpleK8SClient(fabric8Client);
    }

}
