package org.entando.kubernetes.controller.common.example;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;

public class KeycloakServerController extends AbstractDbAwareController {

    private static final Logger LOGGER = Logger.getLogger(KeycloakServerController.class.getName());

    private SimpleKeycloakClient keycloakClient;

    public void onKeycloakServerAddition(KeycloakServer newKeycloakServer) {
        try {
            k8sClient.entandoResources().updatePhase(newKeycloakServer, EntandoDeploymentPhase.STARTED);
            // Create database for Keycloak
            DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newKeycloakServer, newKeycloakServer.getSpec().getDbms(),
                    "db");
            // Create the Keycloak service using the provided database
            KeycloakDeployable keycloakDeployable = new KeycloakDeployable(newKeycloakServer, databaseServiceResult);
            DeployCommand<KeycloakDeploymentResult> keycloakCommand = new DeployCommand(keycloakDeployable);
            KeycloakDeploymentResult keycloakDeploymentResult = keycloakCommand.execute(k8sClient, Optional.of(keycloakClient));
            if (keycloakCommand.getPod() != null) {
                keycloakClient.login(keycloakDeploymentResult.getExternalBaseUrl(), keycloakDeploymentResult.getUsername(),
                        keycloakDeploymentResult.getPassword());
                keycloakClient.ensureRealm(KubeUtils.ENTANDO_KEYCLOAK_REALM);
                if (newKeycloakServer.getSpec().isDefault()) {
                    k8sClient.secrets().overwriteControllerSecret(new SecretBuilder()
                            .withNewMetadata()
                            .withName(EntandoOperatorConfig.getDefaultKeycloakSecretName())
                            .endMetadata()
                            .addToStringData(KubeUtils.USERNAME_KEY, keycloakDeploymentResult.getUsername())
                            .addToStringData(KubeUtils.PASSSWORD_KEY, keycloakDeploymentResult.getPassword())
                            .addToStringData(KubeUtils.URL_KEY, keycloakDeploymentResult.getExternalBaseUrl())
                            .build());
                }
            }
            k8sClient.entandoResources().updateStatus(newKeycloakServer, keycloakCommand.getStatus());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> format("Unexpected exception occurred while adding KeycloakServer %s/%s",
                    newKeycloakServer.getMetadata().getNamespace(),
                    newKeycloakServer.getMetadata().getName()));
        } finally {
            k8sClient.entandoResources().updatePhase(newKeycloakServer, newKeycloakServer.getStatus().calculateFinalPhase());
        }

    }

}
