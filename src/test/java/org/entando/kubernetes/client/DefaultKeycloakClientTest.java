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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.model.plugin.Permission;
import org.entando.kubernetes.test.integrationtest.common.EntandoOperatorTestConfig;
import org.entando.kubernetes.test.integrationtest.common.FluentIntegrationTesting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

@Tags({@Tag("inter-process"), @Tag("pre-deployment"), @Tag("integration")})
class DefaultKeycloakClientTest implements FluentIntegrationTesting, KeycloakTestHelper {

    public static final String MY_REALM = EntandoOperatorTestConfig.calculateNameSpace("my-realm");
    public static final String MY_CLIENT = "my-client";
    public static final String EXISTING_CLIENT = "existing-client";
    public static final String EXISTING_ROLE = "existing-role";
    private static DefaultKeycloakClient keycloakClient = null;

    @BeforeEach
    void createDefaultKeycloakSecret() {
        this.deleteRealm(MY_REALM);
    }

    @Test
    void testCreatePublicClient() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //When I create the public client in this realm
        kc.createPublicClient(MY_REALM, MY_CLIENT, "http://test.domain.com");
        //Then a new Client should be available
        Optional<ClientRepresentation> publicClient = this.findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient enabled
        assertThat(publicClient.get().isPublicClient(), is(true));
        //And the correct redirectUris and origins configured
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        assertThat(publicClient.get().getWebOrigins().get(0), is("http://test.domain.com"));
    }

    @Test
    void testCreatePublicClientTwice() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //And I have created public client in this realm
        kc.createPublicClient(MY_REALM, MY_CLIENT, "http://test.domain.com");
        //When I create a second public client in this realm
        kc.createPublicClient(MY_REALM, MY_CLIENT, "http://another.domain.com");
        //Then a new Client should be available
        Optional<ClientRepresentation> publicClient = this.findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient enabled
        assertThat(publicClient.get().isPublicClient(), is(true));
        //And the correct redirectUris and origins configured
        assertThat(publicClient.get().getRedirectUris(), containsInAnyOrder("http://test.domain.com/*", "http://another.domain.com/*"));
        assertThat(publicClient.get().getWebOrigins(), containsInAnyOrder("http://test.domain.com", "http://another.domain.com"));
    }

    @Test
    void testPrepareClientWithPermissions() {
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
                .withWebOrigin("http://test.domain.com")
                .withPermission(EXISTING_CLIENT, EXISTING_ROLE)
        );
        //Then a new client should be available
        Optional<ClientRepresentation> publicClient = this.findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient functionality disabled
        assertThat(publicClient.get().isPublicClient(), is(false));
        //With correct redirect uri
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        //With correct web origins
        assertThat(publicClient.get().getWebOrigins().get(0), is("http://test.domain.com"));
        //With correct permissions
        List<RoleRepresentation> roleRepresentations = this.retrieveServiceAccountRolesInRealm(MY_REALM, MY_CLIENT, EXISTING_CLIENT);
        assertThat(roleRepresentations.get(0).getName(), is(EXISTING_ROLE));
    }

    @Test
    void testAssignRoleToServiceAccount() {
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
        //And I create another client in this realm
        kc.prepareClientAndReturnSecret(new KeycloakClientConfig(MY_REALM, MY_CLIENT, MY_CLIENT)
                .withRedirectUri("http://test.domain.com/*")
                .withWebOrigin("http://test.domain.com")
        );
        //When I assign a role in the first client to the second client
        kc.assignRoleToClientServiceAccount(MY_REALM, MY_CLIENT, new Permission(EXISTING_CLIENT, EXISTING_ROLE));
        //Then a new client should be available
        Optional<ClientRepresentation> publicClient = this.findClientInRealm(MY_REALM, MY_CLIENT);
        //With correct permissions
        List<RoleRepresentation> roleRepresentations = this.retrieveServiceAccountRolesInRealm(MY_REALM, MY_CLIENT, EXISTING_CLIENT);
        assertThat(roleRepresentations.get(0).getName(), is(EXISTING_ROLE));
    }

    private DefaultKeycloakClient prepareKeycloak() {
        if (keycloakClient == null) {
            keycloakClient = new DefaultKeycloakClient();
            if (EntandoOperatorTestConfig.lookupProperty(EntandoOperatorTestConfig.ENTANDO_TEST_KEYCLOAK_BASE_URL).isEmpty()) {
                try (DefaultKubernetesClient defaultKubernetesClient = new DefaultKubernetesClient()) {
                    final Secret secret = defaultKubernetesClient.secrets().inNamespace("jx").withName("entando-jx-common-secret").get();
                    keycloakClient.login(decodeData(secret, "keycloak.base.url"),
                            decodeData(secret, "keycloak.admin.user"),
                            decodeData(secret, "keycloak.admin.password"));
                }
            } else {
                keycloakClient.login(EntandoOperatorTestConfig.getKeycloakBaseUrl(),
                        EntandoOperatorTestConfig.getKeycloakUser(),
                        EntandoOperatorTestConfig.getKeycloakPassword());
            }
        }
        return keycloakClient;
    }

    private String decodeData(Secret secret, String o) {
        return new String(Base64.getDecoder().decode(secret.getData().get(o)), StandardCharsets.UTF_8);
    }

    @Override
    public Keycloak getKeycloak() {
        return prepareKeycloak().getKeycloak();
    }
}
