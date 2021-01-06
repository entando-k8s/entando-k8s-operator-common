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

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginList;
import org.entando.kubernetes.model.plugin.EntandoPluginOperationFactory;

public class EntandoPluginIntegrationTestHelper extends
        IntegrationTestHelperBase<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin> {

    public static final String TEST_PLUGIN_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("plugin-namespace");
    public static final String TEST_PLUGIN_NAME = EntandoOperatorTestConfig.calculateName("test-plugin-a");
    public static final String PAM_CONNECTION_CONFIG = "pam-connection-config";

    public EntandoPluginIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoPluginOperationFactory::produceAllEntandoPlugins);
    }

    public void createAndWaitForPlugin(EntandoPlugin plugin, boolean hasContainerizedDb) {
        // And a secret named pam-connection
        client.secrets().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).createOrReplace(new SecretBuilder()
                .withNewMetadata()
                .withName(PAM_CONNECTION_CONFIG)
                .endMetadata()
                .withType("Opaque")
                .addToStringData("config.yaml", "thisis: nothing")
                .build());
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> client.secrets().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                        .withName(PAM_CONNECTION_CONFIG).get() != null);
        getOperations()
                .inNamespace(TEST_PLUGIN_NAMESPACE).create(plugin);
        // Then I expect to see
        // 1. A deployment for the plugin, with a name that starts with the plugin name and ends with
        // "-deployment" and a single port for 8081
        if (hasContainerizedDb) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(120)),
                    TEST_PLUGIN_NAMESPACE, plugin.getMetadata().getName() + "-db");
        }

        waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(60)), TEST_PLUGIN_NAMESPACE,
                plugin.getMetadata().getName() + "-server-db-preparation-job");
        waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(240)),
                TEST_PLUGIN_NAMESPACE, plugin.getMetadata().getName() + "-server");
        Resource<EntandoPlugin, DoneableEntandoPlugin> pluginResource = getOperations()
                .inNamespace(TEST_PLUGIN_NAMESPACE).withName(plugin.getMetadata().getName());
        //Wait for widget registration too - sometimes we get 503's for about 3 attempts
        await().atMost(240, SECONDS).until(() -> {
            EntandoCustomResourceStatus status = pluginResource.fromServer().get().getStatus();
            return status.forServerQualifiedBy("server").isPresent()
                    && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
        });
    }

}
