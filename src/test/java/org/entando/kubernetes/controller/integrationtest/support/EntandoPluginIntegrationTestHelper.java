package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginList;

public class EntandoPluginIntegrationTestHelper extends AbstractIntegrationTestHelper {

    public static final String TEST_PLUGIN_NAME = "test-plugin-a";
    public static final String TEST_PLUGIN_NAMESPACE = "plugin-namespace";
    public static final String PAM_CONNECTION_CONFIG = "pam-connection-config";
    private CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList,
            DoneableEntandoPlugin> entandoPluginOperations;

    public EntandoPluginIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client);
    }

    public CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin> getEntandoPluginOperations() {
        if (entandoPluginOperations == null) {
            this.entandoPluginOperations = this.entandoPluginsInAnyNamespace();
        }
        return entandoPluginOperations;
    }

    public void createAndWaitForPlugin(EntandoPlugin plugin, boolean isDbEmbedded) {
        // And a secret named pam-connection
        client.secrets().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).createOrReplace(new SecretBuilder()
                .withNewMetadata()
                .withName(PAM_CONNECTION_CONFIG)
                .endMetadata()
                .withType("Opaque")
                .addToStringData("config.yaml", "thisis: nothing")
                .build());
        await().atMost(30, TimeUnit.SECONDS).ignoreExceptions()
                .until(() -> client.secrets().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                        .withName(PAM_CONNECTION_CONFIG).get() != null);
        plugin.getMetadata().setName(TEST_PLUGIN_NAME);
        getEntandoPluginOperations()
                .inNamespace(TEST_PLUGIN_NAMESPACE).create(plugin);
        // Then I expect to see
        // 1. A deployment for the plugin, with a name that starts with the plugin name and ends with
        // "-deployment" and a single port for 8081
        if (isDbEmbedded) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(120)),
                    TEST_PLUGIN_NAMESPACE, TEST_PLUGIN_NAME + "-db");
        }
        waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(60)), TEST_PLUGIN_NAMESPACE,
                TEST_PLUGIN_NAME + "-db-preparation-job");
        waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(240)),
                TEST_PLUGIN_NAMESPACE, TEST_PLUGIN_NAME + "-server");
        Resource<EntandoPlugin, DoneableEntandoPlugin> pluginResource = getEntandoPluginOperations()
                .inNamespace(TEST_PLUGIN_NAMESPACE).withName(TEST_PLUGIN_NAME);
        //Wait for widget registration too - sometimes we get 503's for about 3 attempts
        waitFor(240).seconds().orUntil(() -> {
            EntandoCustomResourceStatus status = pluginResource.fromServer().get().getStatus();
            return status.forServerQualifiedBy("server").isPresent()
                    && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
        });
    }

    private CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList,
            DoneableEntandoPlugin> entandoPluginsInAnyNamespace() {

        CustomResourceDefinition entandoPluginCrd = client.customResourceDefinitions()
                .withName(EntandoPlugin.CRD_NAME).get();
        return (CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin>) client
                .customResources(entandoPluginCrd, EntandoPlugin.class, EntandoPluginList.class,
                        DoneableEntandoPlugin.class).inAnyNamespace();
    }

}
