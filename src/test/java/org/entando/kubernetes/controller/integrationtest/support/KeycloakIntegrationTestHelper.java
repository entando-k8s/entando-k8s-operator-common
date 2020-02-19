package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.KubeUtils.ENTANDO_KEYCLOAK_REALM;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.ClientErrorException;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.keycloakserver.DoneableEntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
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

    public KeycloakIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoKeycloakServerOperationFactory::produceAllEntandoKeycloakServers);
    }

    public boolean ensureKeycloak() {
        EntandoKeycloakServer keycloakServer = getOperations()
                .inNamespace(KEYCLOAK_NAMESPACE)
                .withName(KEYCLOAK_NAME).get();
        if (keycloakServer == null || keycloakServer.getStatus().getEntandoDeploymentPhase() != EntandoDeploymentPhase.SUCCESSFUL) {
            setTestFixture(deleteAll(EntandoKeycloakServer.class).fromNamespace(KEYCLOAK_NAMESPACE));
            createAndWaitForKeycloak(new EntandoKeycloakServerBuilder()
                    .withNewMetadata().withNamespace(KEYCLOAK_NAMESPACE).withName(KEYCLOAK_NAME).endMetadata()
                    .withNewSpec().withDefault(true).withEntandoImageVersion("6.0.0-SNAPSHOT")
                    .withDbms(DbmsVendor.NONE)
                    .withIngressHostName(KEYCLOAK_NAME + "." + getDomainSuffix())
                    .withImageName("entando/entando-keycloak").endSpec()
                    .build(), 30, true);
            return true;
        }
        return false;
    }

    public void deleteDefaultKeycloakAdminSecret() {
        if (client.secrets().withName(EntandoOperatorConfig.getDefaultKeycloakSecretName()).get() != null) {
            client.secrets().withName(EntandoOperatorConfig.getDefaultKeycloakSecretName()).delete();
        }
    }

    public void createAndWaitForKeycloak(EntandoKeycloakServer keycloakServer, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(KEYCLOAK_NAMESPACE).create(keycloakServer);
        if (keycloakServer.getSpec().getDbms().map(v -> v != DbmsVendor.NONE).orElse(false)) {
            if (deployingDbContainers) {
                waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                        KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-db");
            }
            this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)),
                    KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-db-preparation-job");
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

    public void ensureKeycloakClient(String clientId, List<String> roles) {
        KeycloakClientConfig config = new KeycloakClientConfig(ENTANDO_KEYCLOAK_REALM, clientId, clientId);
        for (String role : roles) {
            config = config.withRole(role);
        }
        getDefaultKeycloakClient().orElseThrow(IllegalStateException::new).prepareClientAndReturnSecret(config);
    }

    protected Optional<DefaultKeycloakClient> getDefaultKeycloakClient() {
        return getKeycloak().map(DefaultKeycloakClient::new);
    }

    private Optional<Keycloak> getKeycloak() {
        return getAdminSecret()
                .map(KeycloakConnectionSecret::new)
                .map(connectionConfig -> KeycloakBuilder.builder()
                        .serverUrl(connectionConfig.getBaseUrl())
                        .grantType(OAuth2Constants.PASSWORD)
                        .realm("master")
                        .clientId("admin-cli")
                        .username(connectionConfig.getUsername())
                        .password(connectionConfig.getPassword())
                        .build());
    }

    protected Optional<Secret> getAdminSecret() {
        return Optional.ofNullable(client.secrets()
                .inNamespace(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE)
                .withName(EntandoOperatorConfig.getDefaultKeycloakSecretName())
                .fromServer().get());
    }

    //Because we don't know the state of the Keycloak Client
    public void deleteKeycloakClients(String... clientid) {
        try {
            getKeycloak().ifPresent(keycloak -> {
                ClientsResource clients = keycloak.realm(ENTANDO_KEYCLOAK_REALM).clients();
                Arrays.stream(clientid).forEach(s -> clients.findByClientId(s).stream().forEach(c -> {
                    logWarning("Deleting KeycloakClient " + c.getClientId());
                    clients.get(c.getId()).remove();
                }));
            });
        } catch (Exception e) {
            logWarning(e.toString());
        }

    }

    public List<RoleRepresentation> retrieveServiceAccountRoles(String serviceAccountClientId, String targetClientId) {
        return retrieveServiceAccountRolesInRealm(ENTANDO_KEYCLOAK_REALM, serviceAccountClientId, targetClientId);
    }

    public List<RoleRepresentation> retrieveServiceAccountRolesInRealm(String realmName, String serviceAccountClientId,
            String targetClientId) {
        RealmResource realm = getKeycloak().orElseThrow(IllegalStateException::new).realm(realmName);
        ClientsResource clients = realm.clients();
        ClientRepresentation serviceAccountClient = clients.findByClientId(serviceAccountClientId).get(0);
        ClientRepresentation targetClient = clients.findByClientId(targetClientId).get(0);
        UserRepresentation serviceAccountUser = clients.get(serviceAccountClient.getId()).getServiceAccountUser();
        return realm.users().get(serviceAccountUser.getId()).roles().clientLevel(targetClient.getId()).listAll();
    }

    public Optional<ClientRepresentation> findClientById(String clientId) {
        return findClientInRealm(ENTANDO_KEYCLOAK_REALM, clientId);
    }

    public Optional<ClientRepresentation> findClientInRealm(String realmName, String clientId) {
        try {
            RealmResource realm = getKeycloak().orElseThrow(IllegalStateException::new).realm(realmName);
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
}
