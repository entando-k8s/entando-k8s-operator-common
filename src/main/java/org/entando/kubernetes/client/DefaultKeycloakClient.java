package org.entando.kubernetes.client;

import static java.lang.Thread.sleep;
import static java.util.Optional.ofNullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.model.plugin.ExpectedRole;
import org.entando.kubernetes.model.plugin.Permission;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
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
    private static final Logger LOGGER = Logger.getLogger(DefaultKeycloakClient.class.getName());
    private static final int MAX_RETRY_COUNT = 60;
    private Keycloak keycloak;
    private boolean isHttps = false;

    private static RoleRepresentation toRoleRepresentation(ExpectedRole expectedRole) {
        RoleRepresentation roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(expectedRole.getCode());
        roleRepresentation.setDescription(expectedRole.getName());
        return roleRepresentation;
    }

    @Override
    public void login(String baseUrl, String username, String password) {
        isHttps = baseUrl.toLowerCase().startsWith("https");
        ResteasyClient sslClient = ((ResteasyClientBuilder) ClientBuilder.newBuilder()).connectionPoolSize(10)
                .hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.ANY).build();
        keycloak = KeycloakBuilder.builder()
                .serverUrl(baseUrl)
                .resteasyClient(sslClient)
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
                if (isKeycloakAvailable()) {
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
    }

    private boolean isKeycloakAvailable() {
        try {
            keycloak.realm(MASTER_REALM).toRepresentation();
            return true;
        } catch (ForbiddenException | NotAuthorizedException e) {
            //Could be valid - no access to master
            return true;
        } catch (ServiceUnavailableException | NotFoundException e) {
            return false;
        }
    }

    @Override
    public void ensureRealm(String realm) {
        RealmResource realmResource = keycloak.realm(realm);
        RealmRepresentation realmRepresentation = getRealmRepresentation(realmResource);
        if (realmRepresentation == null) {
            optionallyDisableSslForNonProductionEnvironments();
            RealmRepresentation newRealm = new RealmRepresentation();
            newRealm.setEnabled(true);
            newRealm.setRealm(realm);
            newRealm.setSslRequired(SslRequired.NONE.name());
            newRealm.setDisplayName(realm);
            keycloak.realms().create(newRealm);
            createFirstUser(realmResource);
            createOperatorClient(realmResource);
        }
    }

    private void optionallyDisableSslForNonProductionEnvironments() {
        if (EntandoOperatorConfig.disableKeycloakSslRequirement() && !isHttps) {
            try {
                RealmResource realmResource = keycloak.realm(MASTER_REALM);
                RealmRepresentation master = realmResource.toRepresentation();
                master.setSslRequired(SslRequired.NONE.name());
                realmResource.update(master);
            } catch (ClientErrorException e) {
                LOGGER.log(Level.WARNING, "Could not disable SSL for master realm");
            }
        }
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
    public void createPublicClient(String realm, String domain) {
        RealmResource realmResource = keycloak.realm(realm);
        createPublicClient(realmResource, domain);
    }

    private void createPublicClient(RealmResource realmResource, String domain) {
        List<ClientRepresentation> existing = realmResource.clients().findByClientId(KubeUtils.PUBLIC_CLIENT_ID);
        if (existing.isEmpty()) {
            ClientRepresentation client = new ClientRepresentation();
            client.setName("Entando WEB");
            client.setClientId(KubeUtils.PUBLIC_CLIENT_ID);
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

    //TODO this is a signficant security risk but should fall away with App/Plugin decoupling
    private void createOperatorClient(RealmResource realmResource) {
        ClientRepresentation client = new ClientRepresentation();
        client.setName("Entando K8s Operator");
        client.setClientId(KubeUtils.OPERATOR_CLIENT_ID);
        client.setSecret(KubeUtils.OPERATOR_CLIENT_ID);
        client.setEnabled(true);
        client.setStandardFlowEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        client.setServiceAccountsEnabled(true);
        client.setAuthorizationServicesEnabled(false);
        realmResource.clients().create(client);
    }

    private void createFirstUser(RealmResource realmResource) {
        final UserRepresentation user = new UserRepresentation();
        user.setUsername("admin");
        user.setEnabled(true);
        Response response = realmResource.users().create(user);
        CredentialRepresentation credentials = new CredentialRepresentation();
        credentials.setValue("adminadmin");
        credentials.setTemporary(true);
        credentials.setType(KubeUtils.PASSSWORD_KEY);
        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
        realmResource.users().get(userId).resetPassword(credentials);
    }

    @Override
    public String prepareClientAndReturnSecret(KeycloakClientConfig config) {
        String id = findOrCreateClient(config);
        updateClient(config, id);
        return keycloak.realm(config.getRealm()).clients().get(id).getSecret().getValue();
    }

    private String findOrCreateClient(KeycloakClientConfig config) {
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
            Response response = realmResource.clients().create(client);
            String id = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            realmResource.clients().get(id).generateNewSecret();
            return id;
        }
    }

    private Optional<ClientRepresentation> findClient(KeycloakClientConfig config) {
        return keycloak.realm(config.getRealm()).clients()
                .findByClientId(config.getClientId())
                .stream().findFirst();
    }

    @Override
    public void updateClient(KeycloakClientConfig config) {
        findClient(config).ifPresent(client -> updateClient(config, client.getId()));
    }

    @SuppressWarnings("squid:S1155")//Because having a double negative in an if statement reduces readibility
    private void updateClient(KeycloakClientConfig config, String id) {
        RealmResource realmResource = keycloak.realm(config.getRealm());
        ClientResource clientResource = realmResource.clients().get(id);
        //TODO this will never be null - this could result in accidental updates.
        ofNullable(config.getRoles()).ifPresent(roles -> {
            List<RoleRepresentation> list = clientResource.roles().list();
            Set<String> desiredRoleNames = roles.stream()
                    .map(ExpectedRole::getName)
                    .collect(Collectors.toSet());
            List<String> currentRoleNames = list.stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());

            currentRoleNames.stream()
                    .filter(roleName -> !desiredRoleNames.contains(roleName))
                    .forEach(clientResource.roles()::deleteRole);
            roles.stream().filter(role -> !currentRoleNames.contains(role.getName()))
                    .map(DefaultKeycloakClient::toRoleRepresentation)
                    .forEach(clientResource.roles()::create);
        });
        ofNullable(config.getPermissions())
                .ifPresent(list -> list.forEach(role -> assignServiceAccountRole(realmResource, clientResource, role)));
        List<String> redirectUris = config.getRedirectUris();
        if (redirectUris.size() > 0) {
            ClientRepresentation clientRepresentation = clientResource.toRepresentation();
            clientRepresentation.getRedirectUris().addAll(redirectUris);
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

}
