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

import static java.lang.Thread.sleep;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.plugin.ExpectedRole;
import org.entando.kubernetes.model.plugin.Permission;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class DefaultKeycloakClient implements SimpleKeycloakClient {

    public static final String MASTER_REALM = "master";
    public static final String EXCEPTION_RESOLVING_MASTER_REALM_ON_KEYCLOAK = "Exception resolving master realm on Keycloak";
    private static final Logger LOGGER = Logger.getLogger(DefaultKeycloakClient.class.getName());
    private static final int MAX_RETRY_COUNT = 180;
    private Keycloak keycloak;
    private boolean isHttps = false;
    private String currentUser;
    private String currentBaseUrl;

    public DefaultKeycloakClient() {
    }

    public DefaultKeycloakClient(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    private static RoleRepresentation toRoleRepresentation(ExpectedRole expectedRole) {
        RoleRepresentation roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(expectedRole.getCode());
        roleRepresentation.setDescription(expectedRole.getName());
        return roleRepresentation;
    }

    @Override
    public void login(String baseUrl, String username, String password) {
        if (baseUrl.equals(currentBaseUrl) && username.equals(currentUser) && keycloak != null) {
            return;
        }
        this.currentBaseUrl = baseUrl;
        this.currentUser = username;
        isHttps = baseUrl.toLowerCase().startsWith("https");
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.register(EntandoJackson2Provider.class);
        final Keycloak attemptedKeycloak = KeycloakBuilder.builder()
                .resteasyClient((ResteasyClient) clientBuilder.build())
                .serverUrl(baseUrl)
                .grantType(OAuth2Constants.PASSWORD)
                .realm(MASTER_REALM)
                .clientId("admin-cli")
                .username(username)
                .password(password)
                .build();
        int count = 0;
        boolean connectionFailed = true;
        while (connectionFailed) {
            try {
                if (isKeycloakAvailable(attemptedKeycloak)) {
                    connectionFailed = false;
                } else {
                    count++;
                    if (count > MAX_RETRY_COUNT) {
                        throw new IllegalStateException("Could not connect to " + baseUrl);
                    } else {
                        sleep(1000);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Should not happen", e);
                Thread.currentThread().interrupt();
            }
        }
        this.keycloak = attemptedKeycloak;
    }

    private static boolean isKeycloakAvailable(Keycloak keycloak) {
        try {
            keycloak.realm(MASTER_REALM).toRepresentation();
            return true;
        } catch (ProcessingException e) {
            LOGGER.log(Level.SEVERE, EXCEPTION_RESOLVING_MASTER_REALM_ON_KEYCLOAK, e);
            return e.getCause() instanceof ForbiddenException || e.getCause() instanceof NotAuthorizedException;
        } catch (ForbiddenException | NotAuthorizedException e) {
            LOGGER.log(Level.SEVERE, EXCEPTION_RESOLVING_MASTER_REALM_ON_KEYCLOAK, e);
            //Could be valid - no access to master
            return true;
        } catch (ServiceUnavailableException | NotFoundException e) {
            LOGGER.log(Level.SEVERE, EXCEPTION_RESOLVING_MASTER_REALM_ON_KEYCLOAK, e);
            return false;
        }
    }

    @Override
    public void ensureRealm(String realm) {
        RealmResource realmResource = keycloak.realm(realm);
        RealmRepresentation realmRepresentation = getRealmRepresentation(realmResource);
        if (realmRepresentation == null) {
            RealmRepresentation newRealm = new RealmRepresentation();
            newRealm.setEnabled(true);
            newRealm.setRealm(realm);
            if (shouldDisableSsl()) {
                newRealm.setSslRequired(SslRequired.NONE.name().toLowerCase());
            }
            newRealm.setDisplayName(realm);
            keycloak.realms().create(newRealm);
            createFirstUser(realmResource);
        }
    }

    private boolean shouldDisableSsl() {
        return EntandoOperatorConfig.disableKeycloakSslRequirement() && !isHttps;
    }

    @Override
    public void assignRoleToClientServiceAccount(String realm, String serviceAccountClientId, Permission serviceRole) {
        RealmResource realmResource = keycloak.realm(realm);
        Optional<ClientResource> clientResource = findByClientId(realmResource, serviceAccountClientId);
        assignServiceAccountRole(realmResource, clientResource.orElseThrow(IllegalArgumentException::new), serviceRole);
    }

    private RealmRepresentation getRealmRepresentation(RealmResource realmResource) {
        try {
            return realmResource.toRepresentation();
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Override
    public void createPublicClient(String realm, String clientId, String domain) {
        ensureRealm(realm);
        RealmResource realmResource = keycloak.realm(realm);
        createPublicClient(realmResource, clientId, domain);
    }

    private void createPublicClient(RealmResource realmResource, String clientId, String domain) {
        List<ClientRepresentation> existing = realmResource.clients().findByClientId(clientId);
        if (existing.isEmpty()) {
            ClientRepresentation client = new ClientRepresentation();
            client.setName("Entando WEB");
            client.setClientId(clientId);
            client.setEnabled(true);
            client.setServiceAccountsEnabled(false);
            client.setStandardFlowEnabled(true);
            client.setImplicitFlowEnabled(true);
            client.setDirectAccessGrantsEnabled(false);
            client.setAuthorizationServicesEnabled(false);
            client.setRedirectUris(Collections.singletonList("*".equals(domain) ? domain : domain + "/*"));
            client.setPublicClient(true);
            client.setOrigin(domain);
            client.setWebOrigins(Collections.singletonList(domain));
            realmResource.clients().create(client);
        } else {
            ClientRepresentation client = existing.get(0);
            client.getRedirectUris().add(domain + "/*");
            client.getWebOrigins().add(domain);
            realmResource.clients().get(client.getId()).update(client);
        }
    }

    private void createFirstUser(RealmResource realmResource) {
        final UserRepresentation user = new UserRepresentation();
        user.setUsername("admin");
        user.setEnabled(true);
        try (Response response = realmResource.users().create(user)) {
            CredentialRepresentation credentials = new CredentialRepresentation();
            credentials.setValue("adminadmin");
            credentials.setTemporary(true);
            credentials.setType(SecretUtils.PASSSWORD_KEY);
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            realmResource.users().get(userId).resetPassword(credentials);
        }
    }

    @Override
    public String prepareClientAndReturnSecret(KeycloakClientConfig config) {
        String id = findOrCreateClient(config);
        updateClientWithId(config, id);
        return keycloak.realm(config.getRealm()).clients().get(id).getSecret().getValue();
    }

    private String findOrCreateClient(KeycloakClientConfig config) {
        ensureRealm(config.getRealm());
        RealmResource realmResource = keycloak.realm(config.getRealm());
        Optional<ClientRepresentation> clientRepresentation = findClient(config);
        if (clientRepresentation.isPresent()) {
            return clientRepresentation.get().getId();
        } else {
            ClientRepresentation client = new ClientRepresentation();
            client.setName(config.getClientName());
            client.setClientId(config.getClientId());
            client.setEnabled(true);
            client.setServiceAccountsEnabled(true);
            client.setStandardFlowEnabled(true);
            client.setImplicitFlowEnabled(true);
            client.setDirectAccessGrantsEnabled(true);
            client.setAuthorizationServicesEnabled(false);
            client.setWebOrigins(config.getWebOrigins());
            try (Response response = realmResource.clients().create(client)) {
                String id = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                realmResource.clients().get(id).generateNewSecret();
                return id;
            }
        }
    }

    private Optional<ClientRepresentation> findClient(KeycloakClientConfig config) {
        return keycloak.realm(config.getRealm()).clients()
                .findByClientId(config.getClientId())
                .stream().findFirst();
    }

    @Override
    public void updateClient(KeycloakClientConfig config) {
        findClient(config).ifPresent(client -> updateClientWithId(config, client.getId()));
    }

    Keycloak getKeycloak() {
        return keycloak;
    }

    //Because having a negative in an if statement reduces readability
    @SuppressWarnings("squid:S1155")
    private void updateClientWithId(KeycloakClientConfig config, String id) {
        RealmResource realmResource = keycloak.realm(config.getRealm());
        ClientResource clientResource = realmResource.clients().get(id);
        List<ExpectedRole> desiredRoles = config.getRoles().stream().filter(distinctByKey(ExpectedRole::getCode))
                .collect(Collectors.toList());
        List<RoleRepresentation> currentRoles = clientResource.roles().list();
        Set<String> desiredRoleNames = desiredRoles.stream()
                .map(ExpectedRole::getName)
                .collect(Collectors.toSet());
        List<String> currentRoleNames = currentRoles.stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
        currentRoleNames.stream()
                .filter(roleName -> !desiredRoleNames.contains(roleName))
                .forEach(clientResource.roles()::deleteRole);
        desiredRoles.stream().filter(role -> !currentRoleNames.contains(role.getName()))
                .map(DefaultKeycloakClient::toRoleRepresentation)
                .forEach(clientResource.roles()::create);
        config.getPermissions().forEach(role -> assignServiceAccountRole(realmResource, clientResource, role));
        List<String> redirectUris = config.getRedirectUris();
        if (redirectUris.size() > 0) {
            ClientRepresentation clientRepresentation = clientResource.toRepresentation();
            clientRepresentation.getRedirectUris().addAll(redirectUris);
            clientResource.update(clientRepresentation);
        }
        List<String> webOrigins = config.getWebOrigins();
        if (webOrigins.size() > 0) {
            ClientRepresentation clientRepresentation = clientResource.toRepresentation();
            clientRepresentation.getWebOrigins().addAll(webOrigins);
            clientResource.update(clientRepresentation);
        }
    }

    private void assignServiceAccountRole(RealmResource realmResource, ClientResource clientResource, Permission serviceRole) {
        findByClientId(realmResource, serviceRole.getClientId()).ifPresent(toAssociateClientResource -> {
            String toAssociateClientUuid = toAssociateClientResource.toRepresentation().getId();
            RoleRepresentation role = toAssociateClientResource.roles().get(serviceRole.getRole()).toRepresentation();
            realmResource.users().get(clientResource.getServiceAccountUser().getId()).roles()
                    .clientLevel(toAssociateClientUuid)
                    .add(Collections.singletonList(role));
        });
    }

    private Optional<ClientResource> findByClientId(RealmResource realmResource, String clientId) {
        ClientsResource clientsResource = realmResource.clients();
        return clientsResource.findByClientId(clientId).stream().findFirst()
                .map(ClientRepresentation::getId)
                .map(clientsResource::get);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
