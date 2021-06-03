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

package org.entando.kubernetes.test.e2etest.helpers;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SsoClientConfig;
import org.entando.kubernetes.controller.support.client.impl.DefaultKeycloakClient;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.FluentIntegrationTesting;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.KeycloakTestHelper;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.KeycloakAwareSpec;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.test.e2etest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.test.e2etest.podwaiters.ServicePodWaiter;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

public class KeycloakE2ETestHelper extends
        E2ETestHelperBase<EntandoKeycloakServer> implements
        FluentIntegrationTesting, KeycloakTestHelper {

    public static final String KEYCLOAK_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("keycloak-namespace");
    public static final String KEYCLOAK_NAME = EntandoOperatorTestConfig.calculateName("test-keycloak");
    public static final String KEYCLOAK_REALM = EntandoOperatorTestConfig.calculateNameSpace("test-realm");
    private DefaultKeycloakClient defaultKeycloakClient;

    public KeycloakE2ETestHelper(DefaultKubernetesClient client) {
        super(client, EntandoKeycloakServer.class);
    }

    public void prepareDefaultKeycloakSecretAndConfigMap() {
        String namespace = client.getNamespace();
        client.configMaps().inNamespace(namespace).createOrReplace(new ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG)
                .endMetadata()
                .addToData(NameUtils.URL_KEY, EntandoOperatorTestConfig.getKeycloakBaseUrl())
                .addToData(NameUtils.INTERNAL_URL_KEY, EntandoOperatorTestConfig.getKeycloakBaseUrl())
                .build());
        client.secrets().createOrReplace(new SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, EntandoOperatorTestConfig.getKeycloakUser())
                .addToStringData(SecretUtils.PASSSWORD_KEY, EntandoOperatorTestConfig.getKeycloakPassword())
                .build());
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
        getOperations().inNamespace(keycloakServer.getMetadata().getNamespace()).create(keycloakServer);
        if (deployingDbContainers) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    keycloakServer.getMetadata().getNamespace(), keycloakServer.getMetadata().getName() + "-db");
        }
        if (requiresDatabaseJob(keycloakServer)) {
            this.waitForDbJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)), keycloakServer, "server");
        }
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(270 + waitOffset)),
                keycloakServer.getMetadata().getNamespace(), keycloakServer.getMetadata().getName() + "-server");
        await().atMost(90, TimeUnit.SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(keycloakServer.getMetadata().getNamespace())
                            .withName(KEYCLOAK_NAME)
                            .fromServer().get().getStatus();
                    return status.getServerStatus("server").isPresent()
                            && status.getPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

    private Boolean requiresDatabaseJob(EntandoKeycloakServer keycloakServer) {
        return keycloakServer.getSpec().getDbms().map(v -> v != DbmsVendor.NONE && v != DbmsVendor.EMBEDDED).orElse(false);
    }

    public void ensureKeycloakClient(KeycloakAwareSpec keycloakAwareSpec, String clientId,
            List<String> roles) {
        SsoClientConfig config = new SsoClientConfig(determineRealm(keycloakAwareSpec), clientId, clientId);
        for (String role : roles) {
            config = config.withRole(role);
        }
        getDefaultKeycloakClient().prepareClientAndReturnSecret(config);
    }

    private DefaultKeycloakClient getDefaultKeycloakClient() {
        this.defaultKeycloakClient = Objects.requireNonNullElseGet(this.defaultKeycloakClient, this::connectToExistingKeycloak);
        return this.defaultKeycloakClient;
    }

    public Keycloak getKeycloak() {
        return getDefaultKeycloakClient().getKeycloak();
    }

    public Keycloak getKeycloakFor(EntandoBaseCustomResource<? extends KeycloakAwareSpec, EntandoCustomResourceStatus> requiresKeycloak) {
        return getKeycloak();
    }

    //Because we don't know the state of the Keycloak Client
    public <T extends KeycloakAwareSpec> void deleteKeycloakClients(
            EntandoBaseCustomResource<T, EntandoCustomResourceStatus> requiresKeycloak, String... clientid) {
        try {
            ClientsResource clients = getKeycloakFor(requiresKeycloak).realm(determineRealm(requiresKeycloak.getSpec())).clients();
            Arrays.stream(clientid).forEach(s -> clients.findByClientId(s).forEach(c -> {
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

    public Optional<ClientRepresentation> findClientById(String realmName, String clientId) {
        return findClientInRealm(realmName, clientId);
    }

}