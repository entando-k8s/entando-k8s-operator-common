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
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.common.examples.MinimalKeycloakContainer;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.integrationtest.support.FluentIntegrationTesting;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.TestFixturePreparation;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.DoneableEntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

@Tags({@Tag("inter-process"), @Tag("pre-deployment")})
public class KeycloakClientTest implements FluentIntegrationTesting {

    public static final String KCP = "7UTcVFN0HzaPQmV4bJDE";//RandomStringUtils.randomAlphanumeric(20);

    public static final String KC_TEST_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("kc-test-namespace");
    public static final String MY_REALM = "my-realm";
    public static final String MY_CLIENT = "my-client";
    public static final String EXISTING_CLIENT = "existing-client";
    public static final String EXISTING_ROLE = "existing-role";
    private static DefaultKeycloakClient keycloakClient = null;
    private final DefaultKubernetesClient client = TestFixturePreparation.newClient();
    private final DefaultSimpleK8SClient simpleK8SClient = new DefaultSimpleK8SClient(client);
    private final KeycloakIntegrationTestHelper helper = new KeycloakIntegrationTestHelper(client) {
        protected Optional<Secret> getAdminSecret() {
            return Optional.ofNullable(
                    new SecretBuilder()
                            .addToStringData(KubeUtils.USERNAME_KEY, "test-admin")
                            .addToStringData(KubeUtils.PASSSWORD_KEY, KCP)
                            .addToStringData(KubeUtils.URL_KEY,
                                    TlsHelper.getDefaultProtocol() + "://test-kc." + helper.getDomainSuffix() + "/auth")
                            .build());
        }
    };
    private EntandoKeycloakServer keycloakServer = new EntandoKeycloakServerBuilder().editMetadata()
            .withName("test-kc")
            .withNamespace(KC_TEST_NAMESPACE)
            .endMetadata()
            .withNewSpec()
            .withDbms(DbmsVendor.NONE)
            .withIngressHostName("test-kc." + helper.getDomainSuffix())
            .withImageName("docker.io/entando/entando-keycloak:6.1.1")//Keycloak 11
            .withReplicas(1)
            .endSpec()
            .build();

    @Test
    public void testEnsureRealm() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //When I ensure that a specific real is available
        kc.ensureRealm(MY_REALM);
        //Then an Operator Client is created under this realm
        Optional<ClientRepresentation> operatorClient = helper
                .findClientInRealm(MY_REALM, KubeUtils.OPERATOR_CLIENT_ID);
        assertThat(operatorClient.isPresent(), is(true));
        //With only basic functionality enabled
        assertThat(operatorClient.get().isStandardFlowEnabled(), is(false));
        assertThat(operatorClient.get().isImplicitFlowEnabled(), is(false));
        assertThat(operatorClient.get().isPublicClient(), is(false));
    }

    @Test
    public void testCreatePublicClient() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //When I create the public client in this realm
        kc.createPublicClient(MY_REALM, "http://test.domain.com");
        //Then a new Client should be available
        Optional<ClientRepresentation> publicClient = helper.findClientInRealm(MY_REALM, KubeUtils.PUBLIC_CLIENT_ID);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient enabled
        assertThat(publicClient.get().isPublicClient(), is(true));
        //And the correct redirectUris and origins configured
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        assertThat(publicClient.get().getWebOrigins().get(0), is("http://test.domain.com"));
    }

    @Test
    public void testPrepareClientWithPermissions() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //And I have created a client
        kc.prepareClientAndReturnSecret(new KeycloakClientConfig(MY_REALM, EXISTING_CLIENT, EXISTING_CLIENT)
                .withRedirectUri("http://existingclient.domain.com/*")
                .withRole(EXISTING_ROLE)
                .withRole(EXISTING_ROLE)//To confirm there is no failure on duplicates
                .withRole(EXISTING_ROLE)
        );
        //When I create the public client in this realm
        kc.prepareClientAndReturnSecret(new KeycloakClientConfig(MY_REALM, MY_CLIENT, MY_CLIENT)
                .withRedirectUri("http://test.domain.com/*")
                .withPermission(EXISTING_CLIENT, EXISTING_ROLE)
        );
        //Then a new client should be available
        Optional<ClientRepresentation> publicClient = helper.findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient functionality disabled
        assertThat(publicClient.get().isPublicClient(), is(false));
        //With correct redirect uri
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        //With correct permissions
        List<RoleRepresentation> roleRepresentations = helper
                .retrieveServiceAccountRolesInRealm(MY_REALM, MY_CLIENT, EXISTING_CLIENT);
        assertThat(roleRepresentations.get(0).getName(), is(EXISTING_ROLE));
    }

    private DefaultKeycloakClient prepareKeycloak() {
        if (keycloakClient == null) {
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT.getJvmSystemProperty(), "true");
            TestFixturePreparation.prepareTestFixture(this.client, deleteAll(EntandoKeycloakServer.class).fromNamespace(KC_TEST_NAMESPACE));
            NonNamespaceOperation<EntandoKeycloakServer,
                    EntandoKeycloakServerList,
                    DoneableEntandoKeycloakServer,
                    Resource<EntandoKeycloakServer,
                            DoneableEntandoKeycloakServer>> operation = helper
                    .getOperations().inNamespace(KC_TEST_NAMESPACE);
            keycloakServer = operation.createOrReplace(keycloakServer);
            ServiceDeploymentResult result = new DeployCommand<>(new TestKeycloakDeployable(keycloakServer))
                    .execute(simpleK8SClient, Optional
                            .empty());
            simpleK8SClient.pods().waitForPod(KC_TEST_NAMESPACE, DeployCommand.DEPLOYMENT_LABEL_NAME, "test-kc-server");
            keycloakClient = new DefaultKeycloakClient();
            keycloakClient.login(TlsHelper.getDefaultProtocol() + "://test-kc." + helper.getDomainSuffix() + "/auth", "test-admin", KCP);
        }
        return keycloakClient;
    }

    private static class TestKeycloakDeployable implements IngressingDeployable<ServiceDeploymentResult> {

        private final List<DeployableContainer> containers;
        private final EntandoKeycloakServer keycloakServer;

        private TestKeycloakDeployable(EntandoKeycloakServer keycloakServer) {
            this.keycloakServer = keycloakServer;
            this.containers = Arrays.asList(new MinimalKeycloakContainer(keycloakServer));
        }

        @Override
        public List<DeployableContainer> getContainers() {
            return containers;
        }

        @Override
        public String getIngressName() {
            return keycloakServer.getMetadata().getName() + "-ingress";
        }

        @Override
        public String getIngressNamespace() {
            return keycloakServer.getMetadata().getNamespace();
        }

        @Override
        public String getNameQualifier() {
            return "server";
        }

        @Override
        public EntandoCustomResource getCustomResource() {
            return keycloakServer;
        }

        @Override
        public ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
            return new ServiceDeploymentResult(service, ingress);
        }

    }

}
