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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.entando.kubernetes.controller.EntandoOperatorComplianceMode;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.common.examples.SampleIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.common.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.database.DbmsVendorConfig;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.integrationtest.support.EntandoPluginIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.FluentIntegrationTesting;
import org.entando.kubernetes.controller.integrationtest.support.HttpTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.K8SIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.TestFixtureRequest;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tags({@Tag("inter-process"), @Tag("pre-deployment"), @Tag("component")})
class AddExampleWithContainerizedDatabaseTest implements FluentIntegrationTesting, InProcessTestUtil {

    public static final String TEST_PLUGIN_NAME = EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAME + "-name-longer-than-32";
    private final K8SIntegrationTestHelper helper = new K8SIntegrationTestHelper();
    private final SampleController<EntandoPluginSpec, EntandoPlugin, SampleExposedDeploymentResult> controller =
            new SampleController<>(
                    helper.getClient()) {
                @Override
                protected Deployable<SampleExposedDeploymentResult, EntandoPluginSpec> createDeployable(
                        EntandoPlugin newEntandoPlugin,
                        DatabaseServiceResult databaseServiceResult, KeycloakConnectionConfig keycloakConnectionConfig) {
                    return new SampleIngressingDbAwareDeployable<>(newEntandoPlugin, databaseServiceResult) {
                        @Override
                        protected List<DeployableContainer> createContainers(EntandoBaseCustomResource<EntandoPluginSpec> entandoResource) {
                            return Collections
                                    .singletonList(new SampleSpringBootDeployableContainer<>(entandoResource, keycloakConnectionConfig));
                        }
                    };
                }
            };

    @BeforeEach
    public void cleanup() {
        TestFixtureRequest fixtureRequest =
                deleteAll(EntandoKeycloakServer.class).fromNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                        .deleteAll(EntandoPlugin.class).fromNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE);
        helper.keycloak().prepareDefaultKeycloakSecretAndConfigMap();
        //Recreate all namespaces as they depend on previously created Keycloak clients that are now invalid
        helper.setTextFixture(fixtureRequest);
    }

    @AfterEach
    public void afterwards() {
        helper.releaseAllFinalizers();
        helper.afterTest();
        helper.keycloak().deleteDefaultKeycloakAdminSecret();
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty());
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    void create(DbmsVendor dbmsVendor, EntandoOperatorComplianceMode complianceMode) {
        //When I create a EntandoPlugin and I specify it to use PostgreSQL
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(),
                complianceMode.name());
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty(),
                "redhat-registry");
        EntandoPlugin entandoPlugin = new EntandoPluginBuilder().withNewMetadata()
                .withName(TEST_PLUGIN_NAME)
                .withNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                .endMetadata().withNewSpec()
                .withIngressHostName(TEST_PLUGIN_NAME + "." + helper.getDomainSuffix())
                .withImage("entando/entando-avatar-plugin")
                .withNewKeycloakToUse().withRealm(KeycloakIntegrationTestHelper.KEYCLOAK_REALM).endKeycloakToUse()
                .withDbms(dbmsVendor)
                .endSpec().build();
        helper.entandoPlugins()
                .listenAndRespondWithStartupEvent(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE, controller::onStartup);
        helper.entandoPlugins().createAndWaitForPlugin(entandoPlugin, true);
        //Then I expect to see
        verifyDatabaseDeployment(dbmsVendor);
        verifyPluginDeployment();
    }

    private static Stream<Arguments> provideStringsForIsBlank() {
        return Stream.of(
                Arguments.of(DbmsVendor.POSTGRESQL, EntandoOperatorComplianceMode.COMMUNITY),
                Arguments.of(DbmsVendor.POSTGRESQL, EntandoOperatorComplianceMode.REDHAT),
                Arguments.of(DbmsVendor.MYSQL, EntandoOperatorComplianceMode.COMMUNITY),
                Arguments.of(DbmsVendor.MYSQL, EntandoOperatorComplianceMode.REDHAT)
        );
    }

    private void verifyDatabaseDeployment(DbmsVendor dbmsVendor) {
        KubernetesClient client = helper.getClient();
        Deployment deployment = client.apps().deployments()
                .inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                .withName(TEST_PLUGIN_NAME + "-db-deployment")
                .get();
        assertThat(thePortNamed(DB_PORT).on(theContainerNamed("db-container").on(deployment))
                .getContainerPort(), equalTo(DbmsVendorConfig.valueOf(dbmsVendor.name()).getDefaultPort()));
        Service service = client.services().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName(
                TEST_PLUGIN_NAME + "-db-service").get();
        assertThat(thePortNamed(DB_PORT).on(service).getPort(), equalTo(DbmsVendorConfig.valueOf(dbmsVendor.name()).getDefaultPort()));
        assertThat(deployment.getStatus().getReadyReplicas(), greaterThanOrEqualTo(1));
        assertThat("It has a db status", helper.entandoPlugins().getOperations()
                .inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                .withName(TEST_PLUGIN_NAME)
                .fromServer().get().getStatus().forDbQualifiedBy("db").isPresent());
    }

    protected void verifyPluginDeployment() {
        String http = HttpTestHelper.getDefaultProtocol();
        KubernetesClient client = helper.getClient();
        await().atMost(15, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).ignoreExceptions().until(() -> HttpTestHelper
                .statusOk(http + "://" + TEST_PLUGIN_NAME + "." + helper.getDomainSuffix()
                        + "/k8s/actuator/health"));
        Deployment deployment = client.apps().deployments().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                .withName(TEST_PLUGIN_NAME + "-server-deployment").get();
        assertThat(thePortNamed("server-port")
                        .on(theContainerNamed("server-container").on(deployment))
                        .getContainerPort(),
                is(8084));
        Service service = client.services().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName(
                TEST_PLUGIN_NAME + "-server-service").get();
        assertThat(thePortNamed("server-port").on(service).getPort(), is(8084));
        assertTrue(deployment.getStatus().getReadyReplicas() >= 1);
        assertTrue(helper.entandoPlugins().getOperations()
                .inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                .withName(TEST_PLUGIN_NAME)
                .fromServer().get().getStatus().forServerQualifiedBy("server").isPresent());
    }

}
