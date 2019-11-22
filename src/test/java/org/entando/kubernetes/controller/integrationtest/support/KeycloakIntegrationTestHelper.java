package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.KubeUtils.ENTANDO_KEYCLOAK_REALM;
import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.ClientErrorException;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.example.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.keycloakserver.DoneableKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerBuilder;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerList;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerOperationFactory;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakIntegrationTestHelper extends AbstractIntegrationTestHelper {

    public static final String KEYCLOAK_NAME = "test-keycloak";
    public static final String KEYCLOAK_NAMESPACE = "keycloak-namespace";
    private CustomResourceOperationsImpl<KeycloakServer, KeycloakServerList,
            DoneableKeycloakServer> keycloakServerOperations;

    public KeycloakIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client);
    }

    public boolean ensureKeycloak() {
        KeycloakServer keycloakServer = getKeycloakServerOperations()
                .inNamespace(KEYCLOAK_NAMESPACE)
                .withName(KEYCLOAK_NAME).get();
        if (keycloakServer == null || keycloakServer.getStatus().getEntandoDeploymentPhase() != EntandoDeploymentPhase.SUCCESSFUL) {
            recreateNamespaces(KEYCLOAK_NAMESPACE);
            createAndWaitForKeycloak(new KeycloakServerBuilder()
                    .withNewMetadata().withNamespace(KEYCLOAK_NAMESPACE).withName(KEYCLOAK_NAME).endMetadata()
                    .withNewSpec().withDefault(true).withEntandoImageVersion("6.0.0-SNAPSHOT")
                    .withDbms(DbmsImageVendor.POSTGRESQL)
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

    public void createAndWaitForKeycloak(KeycloakServer keycloakServer, int waitOffset, boolean deployingDbContainers) {
        getKeycloakServerOperations().inNamespace(KEYCLOAK_NAMESPACE)
                .create(keycloakServer);
        if (deployingDbContainers) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-db");
        }
        this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)),
                KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-db-preparation-job");
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(270 + waitOffset)),
                KEYCLOAK_NAMESPACE, KEYCLOAK_NAME + "-server");
        waitFor(90).seconds().orUntil(
                () -> {
                    EntandoCustomResourceStatus status = getKeycloakServerOperations()
                            .inNamespace(KEYCLOAK_NAMESPACE)
                            .withName(KEYCLOAK_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("server").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

    private Optional<Keycloak> getKeycloak() {
        Optional<Secret> adminSecret = Optional.ofNullable(client.secrets()
                .inNamespace(AbstractIntegrationTestHelper.ENTANDO_CONTROLLERS)
                .withName(EntandoOperatorConfig.getDefaultKeycloakSecretName())
                .fromServer().get());
        return adminSecret
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

    public CustomResourceOperationsImpl<KeycloakServer, KeycloakServerList, DoneableKeycloakServer> getKeycloakServerOperations() {
        if (keycloakServerOperations == null) {
            this.keycloakServerOperations = KeycloakServerOperationFactory.produceAllKeycloakServers(client);
        }
        return keycloakServerOperations;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
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
        RealmResource realm = getKeycloak().orElseThrow(IllegalStateException::new).realm(ENTANDO_KEYCLOAK_REALM);
        ClientsResource clients = realm.clients();
        ClientRepresentation serviceAccountClient = clients.findByClientId(serviceAccountClientId).get(0);
        ClientRepresentation targetClient = clients.findByClientId(targetClientId).get(0);
        UserRepresentation serviceAccountUser = clients.get(serviceAccountClient.getId()).getServiceAccountUser();
        return realm.users().get(serviceAccountUser.getId()).roles().clientLevel(targetClient.getId()).listAll();
    }

    public Optional<ClientRepresentation> findClientById(String clientId) {
        try {
            RealmResource realm = getKeycloak().orElseThrow(IllegalStateException::new).realm(ENTANDO_KEYCLOAK_REALM);
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
