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

package org.entando.kubernetes.test.e2etest.helpers;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.EntandoOperatorTestConfig;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginList;
import org.entando.kubernetes.model.plugin.EntandoPluginOperationFactory;
import org.entando.kubernetes.test.e2etest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.test.e2etest.podwaiters.ServicePodWaiter;

public class EntandoPluginE2ETestHelper extends
        E2ETestHelperBase<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin> {

    public static final String TEST_PLUGIN_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("plugin-namespace");
    public static final String TEST_PLUGIN_NAME = EntandoOperatorTestConfig.calculateName("test-plugin-a");
    public static final String PAM_CONNECTION_CONFIG = "pam-connection-config";

    public EntandoPluginE2ETestHelper(DefaultKubernetesClient client) {
        super(client, EntandoPluginOperationFactory::produceAllEntandoPlugins);
    }

    public void createAndWaitForPlugin(EntandoPlugin plugin, boolean hasContainerizedDb) {
        // And a secret named pam-connection
        client.secrets().inNamespace(plugin.getMetadata().getNamespace()).createOrReplace(new SecretBuilder()
                .withNewMetadata()
                .withName(PAM_CONNECTION_CONFIG)
                .endMetadata()
                .withType("Opaque")
                .addToStringData("config.yaml", "thisis: nothing")
                .build());
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> client.secrets().inNamespace(plugin.getMetadata().getNamespace())
                        .withName(PAM_CONNECTION_CONFIG).get() != null);
        getOperations()
                .inNamespace(plugin.getMetadata().getNamespace()).create(plugin);
        // Then I expect to see
        // 1. A deployment for the plugin, with a name that starts with the plugin name and ends with
        // "-deployment" and a single port for 8081
        if (hasContainerizedDb) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(120)),
                    plugin.getMetadata().getNamespace(), plugin.getMetadata().getName() + "-db");
        }
        if (requiresDatabaseJob(plugin)) {
            waitForDbJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(60)), plugin, "server");
        }
        waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(240)),
                plugin.getMetadata().getNamespace(), plugin.getMetadata().getName() + "-server");
        Resource<EntandoPlugin, DoneableEntandoPlugin> pluginResource = getOperations()
                .inNamespace(plugin.getMetadata().getNamespace()).withName(plugin.getMetadata().getName());
        //Wait for widget registration too - sometimes we get 503's for about 3 attempts
        await().atMost(240, SECONDS).until(() -> {
            EntandoCustomResourceStatus status = pluginResource.fromServer().get().getStatus();
            return status.forServerQualifiedBy("server").isPresent()
                    && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
        });
    }

    private Boolean requiresDatabaseJob(EntandoPlugin plugin) {
        return plugin.getSpec().getDbms().map(dbmsVendor -> !(dbmsVendor == DbmsVendor.EMBEDDED || dbmsVendor == DbmsVendor.NONE))
                .orElse(false);
    }

}
