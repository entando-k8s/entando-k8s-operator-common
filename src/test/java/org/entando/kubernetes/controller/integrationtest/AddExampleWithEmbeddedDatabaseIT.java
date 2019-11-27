package org.entando.kubernetes.controller.integrationtest;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.model.DbmsImageVendor.POSTGRESQL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.common.example.TestServerController;
import org.entando.kubernetes.controller.inprocesstest.FluentTraversals;
import org.entando.kubernetes.controller.integrationtest.support.ClusterInfrastructureIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoAppIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoPluginIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.HttpTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.K8SIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.SampleWriter;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("inter-process"), @Tag("smoke-test")})
public class AddExampleWithEmbeddedDatabaseIT implements FluentTraversals {

    static final int KEYCLOAK_DB_PORT = 5432;
    K8SIntegrationTestHelper k8SIntegrationTestHelper = new K8SIntegrationTestHelper();

    @Test
    public void create() {
        //When I create a KeycloakServer and I specify it to use PostgreSQL
        KeycloakServer keycloakServer = new KeycloakServerBuilder().withNewMetadata()
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .withNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .endMetadata().withNewSpec()
                .withImageName("entando/entando-keycloak")
                .withIngressHostName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "." + k8SIntegrationTestHelper.getDomainSuffix())
                .withDbms(POSTGRESQL)
                .withDefault(true)
                .withEntandoImageVersion("6.0.0-SNAPSHOT")
                .endSpec().build();
        SampleWriter.writeSample(keycloakServer, "keycloak-with-embedded-postgresql-db");
        k8SIntegrationTestHelper.keycloak().listen(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE, TestServerController::main);
        k8SIntegrationTestHelper.keycloak().createAndWaitForKeycloak(keycloakServer, 30, true);
        //Then I expect to see
        verifyKeycloakDatabaseDeployment();
        verifyKeycloakDeployment();
    }

    private void verifyKeycloakDatabaseDeployment() {
        KubernetesClient client = k8SIntegrationTestHelper.getClient();
        Deployment deployment = client.apps().deployments()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-db-deployment")
                .get();
        assertThat(thePortNamed(DB_PORT).on(theContainerNamed(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-db-container").on(deployment))
                .getContainerPort(), equalTo(KEYCLOAK_DB_PORT));
        Service service = client.services().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(
                KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-db-service").get();
        assertThat(thePortNamed(DB_PORT).on(service).getPort(), equalTo(KEYCLOAK_DB_PORT));
        assertThat(deployment.getStatus().getReadyReplicas(), greaterThanOrEqualTo(1));
        assertThat("It has a db status", k8SIntegrationTestHelper.keycloak().getOperations()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .fromServer().get().getStatus().forDbQualifiedBy("db").isPresent());
    }

    @BeforeEach
    public void cleanup() {
        //Recreate all namespaces as they depend on previously created Keycloak clients that are now invalid
        k8SIntegrationTestHelper.recreateNamespaces(
                KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE,
                ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE,
                EntandoAppIntegrationTestHelper.TEST_NAMESPACE,
                EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE
        );
    }

    @AfterEach
    public void afterwards() {
        k8SIntegrationTestHelper.afterTest();
    }

    protected void verifyKeycloakDeployment() {
        String http = org.entando.kubernetes.controller.impl.TlsHelper.getDefaultProtocol();
        KubernetesClient client = k8SIntegrationTestHelper.getClient();
        await().atMost(15, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).ignoreExceptions().until(() -> HttpTestHelper
                .statusOk(http + "://" + KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "." + k8SIntegrationTestHelper.getDomainSuffix()
                        + "/auth"));
        Deployment deployment = client.apps().deployments().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-server-deployment").get();
        assertThat(thePortNamed("server-port")
                        .on(theContainerNamed(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-server-container").on(deployment))
                        .getContainerPort(),
                is(8080));
        Service service = client.services().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(
                KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-server-service").get();
        assertThat(thePortNamed("server-port").on(service).getPort(), is(8080));
        assertTrue(deployment.getStatus().getReadyReplicas() >= 1);
        assertTrue(k8SIntegrationTestHelper.keycloak().getOperations()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .fromServer().get().getStatus().forServerQualifiedBy("server").isPresent());
    }
}
