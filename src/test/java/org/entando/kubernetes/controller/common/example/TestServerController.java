package org.entando.kubernetes.controller.common.example;

import static java.lang.String.format;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;

public class TestServerController extends AbstractDbAwareController {

    private static final Logger LOGGER = Logger.getLogger(TestServerController.class.getName());

    private SimpleKeycloakClient keycloakClient;

    public TestServerController(DefaultKubernetesClient kubernetesClient) {
        this(new DefaultSimpleK8SClient(kubernetesClient), new DefaultKeycloakClient());
    }

    public TestServerController(SimpleK8SClient k8sClient, SimpleKeycloakClient keycloakClient) {
        super.k8sClient = k8sClient;
        this.keycloakClient = keycloakClient;
    }

    public static void main(String[] args) {
        Action action = Action
                .valueOf(
                        EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_ACTION).orElseThrow(IllegalArgumentException::new));
        String resourceName = EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAME)
                .orElseThrow(IllegalArgumentException::new);
        String resourceNamespace = EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE)
                .orElseThrow(IllegalArgumentException::new);
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.withTrustCerts(true).withConnectionTimeout(30000).withRequestTimeout(30000);
        EntandoOperatorConfig.getOperatorNamespaceOverride().ifPresent(configBuilder::withNamespace);

        DefaultKubernetesClient kubernetesClient = new DefaultKubernetesClient(configBuilder.build());

        TestServerController testServerController = new TestServerController(kubernetesClient);
        switch (action) {
            case ADDED:
                testServerController.onKeycloakServerAddition(resourceNamespace, resourceName);
                break;
            case DELETED:
                break;
            case MODIFIED:
                break;
            default:
                break;
        }
    }

    public void onKeycloakServerAddition(String resourceNamespace, String resourceName) {
        KeycloakServer newKeycloakServer = k8sClient.entandoResources().load(KeycloakServer.class, resourceNamespace, resourceName);
        try {
            k8sClient.entandoResources().updatePhase(newKeycloakServer, EntandoDeploymentPhase.STARTED);
            // Create database for Keycloak
            DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newKeycloakServer, newKeycloakServer.getSpec().getDbms(),
                    "db");
            // Create the Keycloak service using the provided database
            TestServerDeployable keycloakDeployable = new TestServerDeployable(newKeycloakServer, databaseServiceResult);
            DeployCommand<ServiceDeploymentResult> keycloakCommand = new DeployCommand(keycloakDeployable);
            ServiceDeploymentResult keycloakDeploymentResult = keycloakCommand.execute(k8sClient, Optional.of(keycloakClient));
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
