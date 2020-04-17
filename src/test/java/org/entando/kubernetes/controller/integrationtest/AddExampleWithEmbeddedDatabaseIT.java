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

package org.entando.kubernetes.controller.integrationtest;

import static org.entando.kubernetes.model.DbmsVendor.POSTGRESQL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.common.examples.DbAwareKeycloakContainer;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.SampleIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.integrationtest.support.ClusterInfrastructureIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoAppIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoPluginIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.FluentIntegrationTesting;
import org.entando.kubernetes.controller.integrationtest.support.HttpTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.K8SIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.SampleWriter;
import org.entando.kubernetes.controller.integrationtest.support.TestFixtureRequest;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("inter-process"),@Tag("pre-deployment") , @Tag("smoke-test")})
public class AddExampleWithEmbeddedDatabaseIT implements FluentIntegrationTesting, InProcessTestUtil {

    static final int KEYCLOAK_DB_PORT = 5432;
    private final K8SIntegrationTestHelper helper = new K8SIntegrationTestHelper();
    private final SampleController<EntandoKeycloakServer> controller = new SampleController<EntandoKeycloakServer>(helper.getClient()) {
        @Override
        protected Deployable<ServiceDeploymentResult> createDeployable(EntandoKeycloakServer newEntandoKeycloakServer,
                DatabaseServiceResult databaseServiceResult, KeycloakConnectionConfig keycloakConnectionConfig) {
            return new SampleIngressingDbAwareDeployable<EntandoKeycloakServer>(newEntandoKeycloakServer, databaseServiceResult) {
                @Override
                protected List<DeployableContainer> createContainers(EntandoKeycloakServer entandoResource) {
                    return Arrays.asList(new DbAwareKeycloakContainer());
                }
            };
        }
    };

    @BeforeEach
    public void cleanup() {
        TestFixtureRequest fixtureRequest =
                deleteAll(EntandoKeycloakServer.class).fromNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                        .deleteAll(EntandoClusterInfrastructure.class)
                        .fromNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                        .deleteAll(EntandoApp.class).fromNamespace(EntandoAppIntegrationTestHelper.TEST_NAMESPACE)
                        .deleteAll(EntandoPlugin.class).fromNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                        .deleteAll(EntandoAppPluginLink.class).fromNamespace(EntandoAppIntegrationTestHelper.TEST_NAMESPACE);

        //Recreate all namespaces as they depend on previously created Keycloak clients that are now invalid
        helper.setTextFixture(fixtureRequest);
    }

    @AfterEach
    public void afterwards() {
        helper.releaseAllFinalizers();
        helper.afterTest();
    }

    @Test
    public void create() {
        //When I create a EntandoKeycloakServer and I specify it to use PostgreSQL
        EntandoKeycloakServer keycloakServer = new EntandoKeycloakServerBuilder().withNewMetadata()
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .withNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .endMetadata().withNewSpec()
                .withImageName("entando/entando-keycloak")
                .withIngressHostName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "." + helper.getDomainSuffix())
                .withDbms(POSTGRESQL)
                .withDefault(true)
                .withEntandoImageVersion("6.0.0-SNAPSHOT")
                .endSpec().build();
        SampleWriter.writeSample(keycloakServer, "keycloak-with-embedded-postgresql-db");
        //        helper.setTextFixture(
        //                deleteAll(EntandoKeycloakServer.class)
        //                        .fromNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
        //                        .deleteAll(EntandoClusterInfrastructure.class)
        //                        .fromNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
        //                        .deleteAll(EntandoApp.class).fromNamespace(EntandoAppIntegrationTestHelper.TEST_NAMESPACE)
        //                        .deleteAll(EntandoPlugin.class).fromNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
        //                        .deleteAll(EntandoAppPluginLink.class).fromNamespace(EntandoAppIntegrationTestHelper.TEST_NAMESPACE)
        //        );
        this.cleanup();
        helper.keycloak()
                .listenAndRespondWithStartupEvent(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE, controller::onStartup);
        helper.keycloak().createAndWaitForKeycloak(keycloakServer, 30, true);
        //Then I expect to see
        verifyKeycloakDatabaseDeployment();
        verifyKeycloakDeployment();
    }

    private void verifyKeycloakDatabaseDeployment() {
        KubernetesClient client = helper.getClient();
        Deployment deployment = client.apps().deployments()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-db-deployment")
                .get();
        assertThat(thePortNamed(DB_PORT).on(theContainerNamed("db-container").on(deployment))
                .getContainerPort(), equalTo(KEYCLOAK_DB_PORT));
        Service service = client.services().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(
                KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-db-service").get();
        assertThat(thePortNamed(DB_PORT).on(service).getPort(), equalTo(KEYCLOAK_DB_PORT));
        assertThat(deployment.getStatus().getReadyReplicas(), greaterThanOrEqualTo(1));
        assertThat("It has a db status", helper.keycloak().getOperations()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .fromServer().get().getStatus().forDbQualifiedBy("db").isPresent());
    }

    protected void verifyKeycloakDeployment() {
        String http = TlsHelper.getDefaultProtocol();
        KubernetesClient client = helper.getClient();
        await().atMost(15, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).ignoreExceptions().until(() -> HttpTestHelper
                .statusOk(http + "://" + KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "." + helper.getDomainSuffix()
                        + "/auth"));
        Deployment deployment = client.apps().deployments().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE)
                .withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-server-deployment").get();
        assertThat(thePortNamed("server-port")
                        .on(theContainerNamed("server-container").on(deployment))
                        .getContainerPort(),
                is(8080));
        Service service = client.services().inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(
                KeycloakIntegrationTestHelper.KEYCLOAK_NAME + "-server-service").get();
        assertThat(thePortNamed("server-port").on(service).getPort(), is(8080));
        assertTrue(deployment.getStatus().getReadyReplicas() >= 1);
        assertTrue(helper.keycloak().getOperations()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName(KeycloakIntegrationTestHelper.KEYCLOAK_NAME)
                .fromServer().get().getStatus().forServerQualifiedBy("server").isPresent());
    }

}
