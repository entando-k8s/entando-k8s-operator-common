package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.util.Enumeration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig.TestTarget;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public class K8SIntegrationTestHelper implements FluentIntegrationTesting {


    private final DefaultKubernetesClient client = TestFixturePreparation.newClient();
    private final String domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
    private final EntandoPluginIntegrationTestHelper entandoPluginIntegrationTestHelper = new EntandoPluginIntegrationTestHelper(client);
    private final KeycloakIntegrationTestHelper keycloakHelper = new KeycloakIntegrationTestHelper(client);
    private final EntandoAppIntegrationTestHelper entandoAppHelper = new EntandoAppIntegrationTestHelper(client);
    private final EntandoAppPluginLinkIntegrationTestHelper entandoAppPluginLinkHelper = new EntandoAppPluginLinkIntegrationTestHelper(
            client);
    private final ExternalDatabaseIntegrationTestHelper externalDatabaseHelper = new ExternalDatabaseIntegrationTestHelper(client);
    private final ClusterInfrastructureIntegrationTestHelper clusterInfrastructureHelper = new ClusterInfrastructureIntegrationTestHelper(
            client);

    private static void stopStaleWatchersFromFillingUpTheLogs() {
        Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String name = loggerNames.nextElement();
            if (name.contains("WatchConnectionManager")) {
                System.out.println("Reducing logger: " + name);
                Optional.ofNullable(LogManager.getLogManager().getLogger(name))
                        .ifPresent(logger -> logger.setLevel(Level.SEVERE));
            }
        }
    }

    public EntandoAppPluginLinkIntegrationTestHelper appPluginLinks() {
        return entandoAppPluginLinkHelper;
    }

    public ExternalDatabaseIntegrationTestHelper externalDatabases() {
        return externalDatabaseHelper;
    }

    public ClusterInfrastructureIntegrationTestHelper clusterInfrastructure() {
        return clusterInfrastructureHelper;
    }

    public KeycloakIntegrationTestHelper keycloak() {
        return keycloakHelper;
    }

    public EntandoPluginIntegrationTestHelper entandoPlugins() {
        return entandoPluginIntegrationTestHelper;
    }

    public EntandoAppIntegrationTestHelper entandoApps() {
        return this.entandoAppHelper;
    }

    public void afterTest() {
        keycloak().afterTest();
        clusterInfrastructure().afterTest();
        externalDatabases().afterTest();
        entandoPlugins().afterTest();
        entandoApps().afterTest();
        appPluginLinks().afterTest();
        if (EntandoOperatorTestConfig.getTestTarget() == TestTarget.STANDALONE) {
            client.close();
            stopStaleWatchersFromFillingUpTheLogs();
        }

    }

    public DefaultKubernetesClient getClient() {
        return client;
    }

    public void createAndWaitForPlugin(EntandoPlugin plugin, boolean isDbEmbedded) {
        ensureKeycloakAndClusterInfrastructure();
        String name = plugin.getMetadata().getName();
        keycloak().deleteKeycloakClients(name + "-confsvc", name + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER, name + "-sidecar");
        entandoPlugins().createAndWaitForPlugin(plugin, isDbEmbedded);
    }

    public void ensureKeycloakAndClusterInfrastructure() {
        ensureKeycloak();
        clusterInfrastructure().ensureClusterInfrastructure();
    }

    public void createAndWaitForApp(EntandoApp entandoApp, int waitOffset, boolean deployingDbContainers) {
        ensureKeycloakAndClusterInfrastructure();
        keycloak().deleteKeycloakClients("entando-web", EntandoAppIntegrationTestHelper.TEST_APP_NAME + "-de",
                EntandoAppIntegrationTestHelper.TEST_APP_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER);
        entandoApps().createAndWaitForApp(entandoApp, waitOffset, deployingDbContainers);
    }

    public void ensureKeycloak() {
        if (keycloak().ensureKeycloak()) {
            TestFixtureRequest testFixtureRequest = new TestFixtureRequest()
                    .deleteAll(EntandoPlugin.class).fromNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                    .deleteAll(EntandoApp.class).fromNamespace(EntandoAppIntegrationTestHelper.TEST_NAMESPACE)
                    .deleteAll(EntandoClusterInfrastructure.class)
                    .fromNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE);
            setTextFixture(testFixtureRequest);
        }
    }

    public void setTextFixture(TestFixtureRequest request) {
        TestFixturePreparation.prepareTestFixture(this.client, request);
    }

    public void createAndWaitForClusterInfrastructure(EntandoClusterInfrastructure clusterInfrastructure, int timeOffset,
            boolean embbedDb) {
        ensureKeycloak();
        clusterInfrastructure().waitForClusterInfrastructure(clusterInfrastructure, timeOffset, embbedDb);

    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

}
