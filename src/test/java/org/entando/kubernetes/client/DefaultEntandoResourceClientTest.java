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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.io.IOException;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.support.command.CreateExternalServiceCommand;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.WebServerStatus;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.test.common.EntandoOperatorTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultEntandoResourceClientTest extends AbstractK8SIntegrationTest {

    public static final String APP_NAMESPACE = EntandoOperatorTestConfig.calculateName("app-namespace");
    public static final String HTTP_TEST_COM = "http://test.com";
    public static final String HTTP_TEST_SVC_CLUSTER_LOCAL = "http://test.svc.cluster.local";
    public static final String ADMIN = "admin";
    public static final String PASSWORD_01 = "Password01";
    public static final String MY_ECI_CLIENTID = "my-eci-clientid";
    private static final String INFRA_NAMESPACE = EntandoOperatorTestConfig.calculateName("infra-namespace");
    private static final String KEYCLOAK_NAMESPACE = EntandoOperatorTestConfig.calculateName("keycloak-namespace");

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{
                APP_NAMESPACE, INFRA_NAMESPACE, KEYCLOAK_NAMESPACE
        };
    }

    @BeforeEach
    void clearNamespaces() {
        deleteAll(getFabric8Client().configMaps());
        deleteAll(getFabric8Client().customResources(EntandoKeycloakServer.class));
    }

    @Test
    void shouldTrackDeploymentFailedStatus() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status to DeploymentFailed
        getSimpleK8SClient().entandoResources().updateStatus(entandoApp, new WebServerStatus("my-webapp"));
        getSimpleK8SClient().entandoResources().deploymentFailed(entandoApp, new IllegalStateException("nope"));
        final EntandoApp actual = getSimpleK8SClient().entandoResources()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        //The failure reflects on the custom resource
        assertThat(actual.getStatus().getEntandoDeploymentPhase(), is(EntandoDeploymentPhase.FAILED));
        assertThat(actual.getStatus().findCurrentServerStatus().get().getEntandoControllerFailure().getFailedObjectName(),
                is(entandoApp.getMetadata().getNamespace() + "/" + entandoApp.getMetadata().getName()));

    }

    @Test
    void shouldUpdateStatusOfKnownCustomResource() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status
        getSimpleK8SClient().entandoResources().updateStatus(entandoApp, new WebServerStatus("my-webapp"));
        final EntandoApp actual = getSimpleK8SClient().entandoResources()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        //The updated status reflects on the custom resource
        assertTrue(actual.getStatus().forServerQualifiedBy("my-webapp").isPresent());

    }

    @Test
    void shouldUpdatePhaseOfKnownCustomResource() {
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(newTestEntandoApp());
        //When I update its status
        getSimpleK8SClient().entandoResources().updatePhase(entandoApp, EntandoDeploymentPhase.SUCCESSFUL);
        final EntandoApp actual = getSimpleK8SClient().entandoResources()
                .load(EntandoApp.class, entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName());
        assertThat(actual.getStatus().getEntandoDeploymentPhase(), is(EntandoDeploymentPhase.SUCCESSFUL));

    }

    @Test
    void shouldUpdateStatusOfOpaqueCustomResource() throws IOException {
        getFabric8Client().apiextensions().v1beta1().customResourceDefinitions().list();
        //Given I have created an EntandoApp
        final EntandoApp entandoApp = getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(newTestEntandoApp());
        ObjectMapper mapper = new ObjectMapper();
        //But it is represented in an opaque format
        DeserializedEntandoResource deserializedEntandoResource = mapper
                .readValue(mapper.writeValueAsBytes(entandoApp), DeserializedEntandoResource.class);
        //When I update its status
        getSimpleK8SClient().entandoResources().updateStatus(deserializedEntandoResource, new WebServerStatus("my-webapp"));
        //The updated status reflects on the custom resource
        final DeserializedEntandoResource actual = getSimpleK8SClient().entandoResources().reload(deserializedEntandoResource);
        assertTrue(actual.getStatus().forServerQualifiedBy("my-webapp").isPresent());

    }

    @Test
    void shouldResolveKeycloakServerInTheSameNamespace() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata().build();
        getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(r);
        //and the connection configmap was created in the KeycloakServer's namespace
        //and the admin secret was created in the Controller's namespace
        prepareKeycloakConnectionInfo(r);
        //And an entandoApp was created in the same namespace as the EntandoKeycloakServer
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        //When I try to resolve a Keycloak config for the EntandoApp
        KeycloakConnectionConfig config = getSimpleK8SClient().entandoResources()
                .findKeycloak(resource, resource.getSpec()::getKeycloakToUse);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the Keycloak in the SAME namespace as the EntadoApp.
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));

    }

    private void prepareKeycloakConnectionInfo(EntandoKeycloakServer r) {
        getSimpleK8SClient().secrets().createConfigMapIfAbsent(r, new ConfigMapBuilder()
                .withNewMetadata().withName(KeycloakName.forTheConnectionConfigMap(r))
                .endMetadata()
                .addToData(NameUtils.URL_KEY, HTTP_TEST_COM)
                .addToData(NameUtils.INTERNAL_URL_KEY, HTTP_TEST_SVC_CLUSTER_LOCAL)
                .build());
        getSimpleK8SClient().secrets().overwriteControllerSecret(new SecretBuilder()
                .withNewMetadata().withName(KeycloakName.forTheAdminSecret(r))
                .endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, ADMIN)
                .addToStringData(SecretUtils.PASSSWORD_KEY, PASSWORD_01)
                .build());
    }

    @Test
    void shouldResolveDatabaseServiceInSameNamespace() {
        //Given I have deployed an EntandoDatabaseService
        EntandoDatabaseService r = getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(new EntandoDatabaseServiceBuilder()
                .editMetadata()
                .withName("my-database-service")
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withHost("myhost.com")
                .withDbms(DbmsVendor.POSTGRESQL)
                .endSpec()
                .build());
        //And an entandoApp was created in the same namespace as the EntandoDatabaseService
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(resource);
        //And the deployments for the database have been created
        new CreateExternalServiceCommand(r).execute(getSimpleK8SClient());
        //When I try to resolve a ExternalDatabaseDeployment for the EntandoApp
        Optional<ExternalDatabaseDeployment> config = getSimpleK8SClient().entandoResources()
                .findExternalDatabase(resource, DbmsVendor.POSTGRESQL);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the Keycloak in the SAME namespace as the EntadoApp.
        assertThat(config.get().getInternalServiceHostname(), is("my-database-service-db-service." + APP_NAMESPACE + ".svc.cluster.local"));
    }

    @Test
    void shouldFallBackOntoDefaultKeycloakServer() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = getSimpleK8SClient().entandoResources()
                .createOrPatchEntandoResource(new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                        .editMetadata()
                        .withNamespace(KEYCLOAK_NAMESPACE)
                        .endMetadata().build());
        //and the connection configmap was created in the KeycloakServer's namespace
        prepareKeycloakConnectionInfo(r);
        //and the EntandoKeycloakServer in question is marked as the default
        getSimpleK8SClient().entandoResources().loadDefaultConfigMap()
                .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY, r.getMetadata().getName())
                .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY, r.getMetadata().getNamespace())
                .done();
        //And an entandoApp was created in a different namespace as the EntandoKeycloakServer
        EntandoApp resource = getSimpleK8SClient().entandoResources()
                .createOrPatchEntandoResource(new EntandoAppBuilder(newTestEntandoApp())
                        .editMetadata()
                        .withNamespace(APP_NAMESPACE)
                        .endMetadata()
                        .build());
        //And the connection ConfigMap in the EntandoApp's namespace was configured with custom connection info
        getSimpleK8SClient().secrets().createConfigMapIfAbsent(resource, new ConfigMapBuilder()
                .withNewMetadata()
                .withName(KeycloakName.forTheConnectionConfigMap(r))
                .withNamespace(resource.getMetadata().getNamespace())
                .endMetadata()
                .addToData(NameUtils.URL_KEY, "https://custom.com/auth")
                .addToData(NameUtils.INTERNAL_URL_KEY, "https://custom.com/auth")
                .build());
        //When I try to resolve a Keycloak config for the EntandoApp
        KeycloakConnectionConfig config = getSimpleK8SClient().entandoResources()
                .findKeycloak(resource, resource.getSpec()::getKeycloakToUse);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the marked as the DEFAULT keycloak server
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));
    }

    @Test
    void shouldResolveExplicitlySpecifiedKeycloakServerToUse() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = getSimpleK8SClient().entandoResources()
                .createOrPatchEntandoResource(new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                        .editMetadata()
                        .withNamespace(KEYCLOAK_NAMESPACE)
                        .endMetadata().build());
        //and the connection configmap was created in the KeycloakServer's namespace
        prepareKeycloakConnectionInfo(r);
        //And an entandoApp was created in a different namespace as the EntandoKeycloakServer, but explicitly specifying to
        // use the previously created EntandoKeycloakServer
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .editSpec()
                .withNewKeycloakToUse()
                .withName(r.getMetadata().getName())
                .withNamespace(r.getMetadata().getNamespace())
                .endKeycloakToUse()
                .endSpec()
                .build();
        getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(resource);
        //When I try to resolve a Keycloak config for the EntandoApp
        KeycloakConnectionConfig config = getSimpleK8SClient().entandoResources()
                .findKeycloak(resource, resource.getSpec()::getKeycloakToUse);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the marked as the DEFAULT keycloak server
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));

    }

}
