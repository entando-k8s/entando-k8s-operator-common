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

package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import org.entando.kubernetes.client.DefaultEntandoResourceClient;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.keycloakserver.DoneableEntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerList;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerOperationFactory;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakIntegrationTestHelper extends
        IntegrationTestHelperBase<EntandoKeycloakServer, EntandoKeycloakServerList, DoneableEntandoKeycloakServer> implements
        FluentIntegrationTesting {

    public static final String KEYCLOAK_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("keycloak-namespace");
    public static final String KEYCLOAK_NAME = EntandoOperatorTestConfig.calculateName("test-keycloak");
    public static final String KEYCLOAK_REALM = EntandoOperatorTestConfig.calculateNameSpace("test-realm");
    private final DefaultEntandoResourceClient entandoResourceClient;

    public KeycloakIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoKeycloakServerOperationFactory::produceAllEntandoKeycloakServers);
        this.entandoResourceClient = new DefaultEntandoResourceClient(client);
    }

    public void prepareDefaultKeycloakSecretAndConfigMap() {
        String namespace = client.getNamespace();
        client.configMaps().createOrReplaceWithNew()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG)
                .endMetadata()
                .addToData(KubeUtils.URL_KEY, EntandoOperatorTestConfig.getKeycloakBaseUrl())
                .addToData(KubeUtils.INTERNAL_URL_KEY, EntandoOperatorTestConfig.getKeycloakBaseUrl())
                .done();
        client.secrets().createOrReplaceWithNew()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .endMetadata()
                .addToStringData(KubeUtils.USERNAME_KEY, EntandoOperatorTestConfig.getKeycloakUser())
                .addToStringData(KubeUtils.PASSSWORD_KEY, EntandoOperatorTestConfig.getKeycloakPassword())
                .done();
    }

    public void deleteDefaultKeycloakAdminSecret() {
        if (client.secrets().withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET).get() != null) {
            client.secrets().withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET).delete();
        }
        if (client.configMaps().withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG).get() != null) {
            client.configMaps().withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG).delete();
        }
    }

    public void createAndWaitForKeycloak(EntandoKeycloakServer keycloakServer, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(KEYCLOAK_NAMESPACE).create(keycloakServer);
        if (keycloakServer.getSpec().getDbms().map(v -> v != DbmsVendor.NONE && v != DbmsVendor.EMBEDDED).orElse(false)) {
            if (deployingDbContainers) {
                waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                        KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-db");
            }
            this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)),
                    KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-server-db-preparation-job");
        }
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(270 + waitOffset)),
                KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-server");
        await().atMost(90, TimeUnit.SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(KEYCLOAK_NAMESPACE)
                            .withName(KEYCLOAK_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("server").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

    public void ensureKeycloakClient(KeycloakAwareSpec keycloakAwareSpec, String clientId,
            List<String> roles) {
        KeycloakClientConfig config = new KeycloakClientConfig(determineRealm(keycloakAwareSpec), clientId, clientId);
        for (String role : roles) {
            config = config.withRole(role);
        }
        getDefaultKeycloakClient().prepareClientAndReturnSecret(config);
    }

    protected DefaultKeycloakClient getDefaultKeycloakClient() {
        return new DefaultKeycloakClient(getKeycloak());
    }

    private Keycloak getKeycloak() {
        EntandoApp resource = new EntandoApp(new EntandoAppSpec());
        return getKeycloakFor(resource);
    }

    public Keycloak getKeycloakFor(EntandoBaseCustomResource<? extends KeycloakAwareSpec> requiresKeycloak) {
        KeycloakConnectionConfig keycloak = entandoResourceClient.findKeycloak(requiresKeycloak);
        return KeycloakBuilder.builder()
                .serverUrl(keycloak.getExternalBaseUrl())
                .grantType(OAuth2Constants.PASSWORD)
                .realm("master")
                .clientId("admin-cli")
                .username(keycloak.getUsername())
                .password(keycloak.getPassword())
                .build();
    }

    //Because we don't know the state of the Keycloak Client
    public <T extends KeycloakAwareSpec> void deleteKeycloakClients(EntandoBaseCustomResource<T> requiresKeycloak, String... clientid) {
        try {
            ClientsResource clients = getKeycloakFor(requiresKeycloak).realm(determineRealm(requiresKeycloak.getSpec())).clients();
            Arrays.stream(clientid).forEach(s -> clients.findByClientId(s).stream().forEach(c -> {
                logWarning("Deleting KeycloakClient " + c.getClientId());
                clients.get(c.getId()).remove();
            }));
        } catch (Exception e) {
            logWarning(e.toString());
        }

    }

    public List<RoleRepresentation> retrieveServiceAccountRoles(String realmName, String serviceAccountClientId, String targetClientId) {
        return retrieveServiceAccountRolesInRealm(realmName, serviceAccountClientId, targetClientId);
    }

    public List<RoleRepresentation> retrieveServiceAccountRolesInRealm(String realmName, String serviceAccountClientId,
            String targetClientId) {
        RealmResource realm = getKeycloak().realm(realmName);
        ClientsResource clients = realm.clients();
        ClientRepresentation serviceAccountClient = clients.findByClientId(serviceAccountClientId).get(0);
        ClientRepresentation targetClient = clients.findByClientId(targetClientId).get(0);
        UserRepresentation serviceAccountUser = clients.get(serviceAccountClient.getId()).getServiceAccountUser();
        return realm.users().get(serviceAccountUser.getId()).roles().clientLevel(targetClient.getId()).listAll();
    }

    public Optional<ClientRepresentation> findClientById(String realmName, String clientId) {
        return findClientInRealm(realmName, clientId);
    }

    public Optional<ClientRepresentation> findClientInRealm(String realmName, String clientId) {
        try {
            RealmResource realm = getKeycloak().realm(realmName);
            ClientsResource clients = realm.clients();
            return clients.findByClientId(clientId).stream().findFirst();
        } catch (ClientErrorException e) {
            if (e.getResponse() != null && e.getResponse().getEntity() != null) {
                System.out.println(e.toString());
                System.out.println(e.getResponse().getEntity());
            }
            throw e;
        }
    }

    public void deleteRealm(String realm) {
        try {
            getKeycloak().realm(realm).remove();
        } catch (NotFoundException e) {
            //can ignore this
        }
    }
}
