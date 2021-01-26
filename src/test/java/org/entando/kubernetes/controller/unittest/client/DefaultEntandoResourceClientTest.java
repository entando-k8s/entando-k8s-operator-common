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

package org.entando.kubernetes.controller.unittest.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Optional;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.command.CreateExternalServiceCommand;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultEntandoResourceClientTest implements InProcessTestUtil {

    public static final String APP_NAMESPACE = "app-namespace";
    public static final String HTTP_TEST_COM = "http://test.com";
    public static final String HTTP_TEST_SVC_CLUSTER_LOCAL = "http://test.svc.cluster.local";
    public static final String ADMIN = "admin";
    public static final String PASSWORD_01 = "Password01";
    public static final String MY_ECI_CLIENTID = "my-eci-clientid";
    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private DefaultSimpleK8SClient defaultSimpleK8SClient;
    private EntandoResourceClient entandoResourceClient;
    private SecretClient secretClient;

    @BeforeEach
    public void setup() {
        defaultSimpleK8SClient = new DefaultSimpleK8SClient(server.getClient());
        entandoResourceClient = defaultSimpleK8SClient.entandoResources();
        secretClient = defaultSimpleK8SClient.secrets();
    }

    @Test
    void testSameNamespaceKeycloakConfigResolution() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
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
        KeycloakConnectionConfig config = entandoResourceClient.findKeycloak(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the Keycloak in the SAME namespace as the EntadoApp.
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));

    }

    private void prepareKeycloakConnectionInfo(EntandoKeycloakServer r) {
        secretClient.createConfigMapIfAbsent(r, new ConfigMapBuilder()
                .withNewMetadata().withName(KeycloakName.forTheConnectionConfigMap(r))
                .endMetadata()
                .addToData(NameUtils.URL_KEY, HTTP_TEST_COM)
                .addToData(NameUtils.INTERNAL_URL_KEY, HTTP_TEST_SVC_CLUSTER_LOCAL)
                .build());
        secretClient.overwriteControllerSecret(new SecretBuilder()
                .withNewMetadata().withName(KeycloakName.forTheAdminSecret(r))
                .endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, ADMIN)
                .addToStringData(SecretUtils.PASSSWORD_KEY, PASSWORD_01)
                .build());
    }

    @Test
    void testSameNamespaceinfrastructureConfigResolution() {
        //Given I have deployed an EntandoClusterInfrastructure
        EntandoClusterInfrastructure r = new EntandoClusterInfrastructureBuilder(newEntandoClusterInfrastructure())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
        //and the connection configmap was created in the EntandoClusterInfrastructure's namespace
        prepareInfrastructureConnectionInfo(r);
        //And an entandoApp was created in the same namespace as the EntandoClusterInfrastructure
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        //When I try to resolve a InfrastructureConfig for the EntandoApp
        Optional<InfrastructureConfig> config = entandoResourceClient.findInfrastructureConfig(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the Keycloak in the SAME namespace as the EntadoApp.
        assertThat(config.get().getK8SExternalServiceUrl(), is(HTTP_TEST_COM));
        assertThat(config.get().getK8SInternalServiceUrl(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.get().getK8sServiceClientId(), is(MY_ECI_CLIENTID));

    }

    @Test
    void testSameNamespaceDatabaseServiceResolution() {
        //Given I have deployed an EntandoDatabaseService
        EntandoDatabaseService r = new EntandoDatabaseServiceBuilder()
                .editMetadata()
                .withName("my-database-service")
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withHost("myhost.com")
                .withDbms(DbmsVendor.POSTGRESQL)
                .endSpec()
                .build();
        entandoResourceClient.createOrPatchEntandoResource(r);
        //And an entandoApp was created in the same namespace as the EntandoDatabaseService
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        //And the deployments for the database have been created
        new CreateExternalServiceCommand(r).execute(defaultSimpleK8SClient);
        //When I try to resolve a ExternalDatabaseDeployment for the EntandoApp
        Optional<ExternalDatabaseDeployment> config = entandoResourceClient.findExternalDatabase(resource, DbmsVendor.POSTGRESQL);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the Keycloak in the SAME namespace as the EntadoApp.
        assertThat(config.get().getInternalServiceHostname(), is("my-database-service-db-service.app-namespace.svc.cluster.local"));
    }

    @Test
    void testDefaultKeycloakConfigResolution() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                .editMetadata()
                .withNamespace("keycloak-namespace")
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
        //and the connection configmap was created in the KeycloakServer's namespace
        prepareKeycloakConnectionInfo(r);
        //and the EntandoKeycloakServer in question is marked as the default
        entandoResourceClient.loadDefaultConfigMap()
                .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY, r.getMetadata().getName())
                .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY, r.getMetadata().getNamespace())
                .done();
        //And an entandoApp was created in a different namespace as the EntandoKeycloakServer
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        //When I try to resolve a Keycloak config for the EntandoApp
        KeycloakConnectionConfig config = entandoResourceClient.findKeycloak(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the marked as the DEFAULT keycloak server
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));

    }

    @Test
    void testDefaultInfrastructureConfigResolution() {
        //Given I have deployed an EntandoClusterInfrastructure
        EntandoClusterInfrastructure r = new EntandoClusterInfrastructureBuilder(newEntandoClusterInfrastructure())
                .editMetadata()
                .withNamespace("infra-namespace")
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
        //and the connection configmap was created in the EntandoClusterInfrastructure's namespace
        prepareInfrastructureConnectionInfo(r);
        //and the EntandoClusterInfrastructure in question is marked as the default
        entandoResourceClient.loadDefaultConfigMap()
                .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY, r.getMetadata().getName())
                .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY, r.getMetadata().getNamespace())
                .done();
        //And an entandoApp was created in a different namespace as the EntandoClusterInfrastructure
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .build();
        //When I try to resolve a InfrastructureConfig for the EntandoApp
        Optional<InfrastructureConfig> config = entandoResourceClient.findInfrastructureConfig(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap
        //associated with the EntandoClusterInfrastructure in the different namespace
        assertThat(config.get().getK8SExternalServiceUrl(), is(HTTP_TEST_COM));
        assertThat(config.get().getK8SInternalServiceUrl(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.get().getK8sServiceClientId(), is(MY_ECI_CLIENTID));

    }

    @Test
    void testExplicitlySpecifiedKeycloakConfigResolution() {
        //Given I have deployed a keycloak instance
        EntandoKeycloakServer r = new EntandoKeycloakServerBuilder(newEntandoKeycloakServer())
                .editMetadata()
                .withNamespace("keycloak-namespace")
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
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
        //When I try to resolve a Keycloak config for the EntandoApp
        KeycloakConnectionConfig config = entandoResourceClient.findKeycloak(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap and Admin Secret
        //associated with the marked as the DEFAULT keycloak server
        assertThat(config.getExternalBaseUrl(), is(HTTP_TEST_COM));
        assertThat(config.getInternalBaseUrl().get(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.getUsername(), is(ADMIN));
        assertThat(config.getPassword(), is(PASSWORD_01));

    }

    @Test
    void testExplicitlySpecifiedInfrastructureConfigResolution() {
        //Given I have deployed an EntandoClusterInfrastructure
        EntandoClusterInfrastructure r = new EntandoClusterInfrastructureBuilder(newEntandoClusterInfrastructure())
                .editMetadata()
                .withNamespace("infra-namespace")
                .endMetadata().build();
        entandoResourceClient.createOrPatchEntandoResource(r);
        //and the connection configmap was created in the EntandoClusterInfrastructure's namespace
        prepareInfrastructureConnectionInfo(r);
        //And an entandoApp was created in a different namespace as the EntandoClusterInfrastructure,
        //  but explicitly specifying to  use the previously created EntandoClusterInfrastructure
        EntandoApp resource = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withNamespace(APP_NAMESPACE)
                .endMetadata()
                .editSpec()
                .withClusterInfrastructureToUse(r.getMetadata().getNamespace(), r.getMetadata().getName())
                .endSpec()
                .build();
        //When I try to resolve a InfrastructureConfig for the EntandoApp
        Optional<InfrastructureConfig> config = entandoResourceClient.findInfrastructureConfig(resource);
        //Then the EntandoResourceClient has resolved the Connection Configmap
        //associated with the EntandoClusterInfrastructure in the different namespace
        assertThat(config.get().getK8SExternalServiceUrl(), is(HTTP_TEST_COM));
        assertThat(config.get().getK8SInternalServiceUrl(), is(HTTP_TEST_SVC_CLUSTER_LOCAL));
        assertThat(config.get().getK8sServiceClientId(), is(MY_ECI_CLIENTID));

    }

    private void prepareInfrastructureConnectionInfo(EntandoClusterInfrastructure r) {
        secretClient.createConfigMapIfAbsent(r, new ConfigMapBuilder()
                .withNewMetadata().withName(InfrastructureConfig.connectionConfigMapNameFor(r))
                .endMetadata()
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY, HTTP_TEST_COM)
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY, HTTP_TEST_SVC_CLUSTER_LOCAL)
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY, MY_ECI_CLIENTID)
                .build());
    }
}
