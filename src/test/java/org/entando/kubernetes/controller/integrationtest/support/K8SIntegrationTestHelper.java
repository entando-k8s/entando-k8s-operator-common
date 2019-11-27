package org.entando.kubernetes.controller.integrationtest.support;

import static java.util.Collections.singletonMap;
import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.impl.TlsHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorE2ETestConfig.TestTarget;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public class K8SIntegrationTestHelper {

    public static final String ORACLE_HOST = System.getProperty("entando.oracle.host", "10.0.0.100");
    private static final Map<String, String> POD_LABEL = singletonMap("entando-k8s-operator-name", "test-entando-k8s-operator");

    static {
        //For using Openshift's internal registry (Caveat: all the namespaces need image puller access to the entando namespace

        //System.setProperty("entando.k8s.operator.registry", "ampie.dynu.net:32001");
        //System.setProperty("entando.k8s.operator.registry", "docker-registry.default.svc.cluster.local:5000");
        //System.setProperty("entando.k8s.operator.registry", "172.30.1.1:5000");
        //System.setProperty(EntandoOperatorConfig.ENTANDO_USE_AUTO_CERT_GENERATION, "true");
        //System.setProperty(INTEGRATION_TARGET_ENVIRONMENT, K8S);
    }

    private final DefaultKubernetesClient client = IntegrationClientFactory.newClient();
    private final String domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
    private final EntandoPluginIntegrationTestHelper entandoPluginIntegrationTestHelper = new EntandoPluginIntegrationTestHelper(client);
    private final KeycloakIntegrationTestHelper keycloakHelper = new KeycloakIntegrationTestHelper(client);
    private final EntandoAppIntegrationTestHelper entandoAppHelper = new EntandoAppIntegrationTestHelper(client);
    private final EntandoAppPluginLinkIntegrationTestHelper entandoAppPluginLinkHelper = new EntandoAppPluginLinkIntegrationTestHelper(
            client);
    private final ExternalDatabaseTestHelper externalDatabaseHelper = new ExternalDatabaseTestHelper(client);
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

    public ExternalDatabaseTestHelper externalDatabases() {
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
        if (EntandoOperatorE2ETestConfig.getTestTarget() == TestTarget.STANDALONE) {
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
            recreateNamespaces(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE, EntandoAppIntegrationTestHelper.TEST_NAMESPACE,
                    ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE);
        }
    }

    public void recreateNamespaces(String... ns) {
        IntegrationClientFactory
                .recreateNamespaces(this.client, ns);
    }

    public void prepareControllers() {
        if (EntandoOperatorE2ETestConfig.getTestTarget() == TestTarget.K8S) {
            redeployControllers();
        } else {
            //            EntandoControllerMain.start();
        }
        stopStaleWatchersFromFillingUpTheLogs();
    }

    private void redeployControllers() {
        if (client.namespaces().withName(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).get() == null) {
            client.namespaces().createNew().withNewMetadata().withName(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).endMetadata()
                    .done();
        }
        ScalableResource<Deployment, DoneableDeployment> deploymentResource = client.apps().deployments()
                .inNamespace(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).withName("entando-k8s-operator");
        if (deploymentResource.get() != null) {
            deploymentResource.delete();
        }
        waitFor(60).seconds().until(() -> deploymentResource.fromServer().get() == null);
        client.apps().deployments().inNamespace(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).createNew()
                .withNewMetadata().withName("entando-k8s-operator").endMetadata()
                .withNewSpec()
                .withNewSelector().withMatchLabels(POD_LABEL).endSelector().withReplicas(1)
                .withNewTemplate().withNewMetadata().withLabels(POD_LABEL).endMetadata().withNewSpec().addNewContainer()
                .withName("entando-k8s-operator")
                .withImage(EntandoOperatorConfig.getEntandoDockerRegistry() + "/entando/entando-k8s-operator:6.0.0-SNAPSHOT")
                .withImagePullPolicy("Always")
                .withNewReadinessProbe().withNewExec()
                .addToCommand("/bin/sh").addToCommand("-c")
                .addToCommand("cat /tmp/EntandoPluginController.ready /tmp/EntandoAppController.ready /tmp/KeycloakServerController.ready "
                        + "/tmp/EntandoClusterInfrastructureController.ready /tmp/EntandoAppPluginLinkController.ready "
                        + "/tmp/ExternalDatabaseController.ready"
                )
                .endExec().endReadinessProbe()
                .withEnv(
                        buildEnvVars())
                .withVolumeMounts(maybeCreateTlsVolumeMounts())
                .endContainer()
                .withVolumes(maybeCreateTlsVolumes())
                .endSpec()
                .endTemplate()
                .endSpec()
                .done();
        waitFor(180).seconds().until(() -> deploymentResource.fromServer().get().getStatus().getReadyReplicas() == 1);
        waitFor(60).seconds().until(
                () -> client.pods().inNamespace(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).withLabels(POD_LABEL).list()
                        .getItems().get(0)
                        .getStatus()
                        .getContainerStatuses().get(0).getReady());
    }

    private List<EnvVar> buildEnvVars() {
        ArrayList<EnvVar> result = new ArrayList<>();
        result.add(new EnvVar(EntandoOperatorConfig.ENTANDO_K8S_OPERATOR_REGISTRY, EntandoOperatorConfig.getEntandoDockerRegistry(), null));
        result.add(new EnvVar("ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT", "true", null));
        result.add(new EnvVar(EntandoOperatorConfig.ENTANDO_DEFAULT_ROUTING_SUFFIX, domainSuffix, null));
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            StringBuilder sb = new StringBuilder();
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path ->
                    sb.append("/etc/entando/ca/").append(path.getFileName().toString()).append(" "));
            result.add(new EnvVar(EntandoOperatorConfig.ENTANDO_CA_CERT_PATHS, sb.toString().trim(), null));
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new EnvVar(EntandoOperatorConfig.ENTANDO_PATH_TO_TLS_KEYPAIR, "/etc/entando/tls", null));
        }
        return result;
    }

    private List<Volume> maybeCreateTlsVolumes() {
        List<Volume> result = new ArrayList<>();
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            Secret secret = new SecretBuilder().withNewMetadata().withName("ca-cert-secret")
                    .endMetadata().build();
            //Add all available CA Certs. No need to map the trustStore itself - the controller will build this up internally
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData()
                    .put(path.getFileName().toString(), TlsHelper.getInstance().getTlsCaCertBase64(path)));
            client.secrets().inNamespace(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).createOrReplace(secret);
            result.add(new VolumeBuilder().withName("ca-cert-volume").withNewSecret().withSecretName("ca-cert-secret").endSecret()
                    .build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            Secret secret = new SecretBuilder().withNewMetadata().withName("tls-secret")
                    .endMetadata()
                    .addToData(TlsHelper.TLS_KEY, TlsHelper.getInstance().getTlsKeyBase64())
                    .addToData(TlsHelper.TLS_CRT, TlsHelper.getInstance().getTlsCertBase64())
                    .build();
            client.secrets().inNamespace(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE).createOrReplace(secret);
            result.add(new VolumeBuilder().withName("tls-volume").withNewSecret().withSecretName("tls-secret").endSecret()
                    .build());
        }
        return result;
    }

    private List<VolumeMount> maybeCreateTlsVolumeMounts() {
        List<VolumeMount> result = new ArrayList<>();
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            result.add(new VolumeMountBuilder().withName("ca-cert-volume").withMountPath("/etc/entando/ca").build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new VolumeMountBuilder().withName("tls-volume").withMountPath("/etc/entando/tls").build());
        }
        return result;
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
