package org.entando.kubernetes.controller.common.example;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import javax.enterprise.event.Observes;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;

public class TestServerController extends AbstractDbAwareController<KeycloakServer> {

    public TestServerController(DefaultKubernetesClient kubernetesClient) {
        super(kubernetesClient, false);
    }

    public TestServerController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        super(k8sClient, keycloakClient);
    }

    public void onStartup(@Observes StartupEvent event) {
        processCommand();
    }

    protected void processAddition(KeycloakServer newKeycloakServer) {
        // Create database for Keycloak
        DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newKeycloakServer, newKeycloakServer.getSpec().getDbms(),
                "db");
        // Create the Keycloak service using the provided database
        TestServerDeployable keycloakDeployable = new TestServerDeployable(newKeycloakServer, databaseServiceResult);
        DeployCommand<ServiceDeploymentResult> keycloakCommand = new DeployCommand<>(keycloakDeployable);
        ServiceDeploymentResult keycloakDeploymentResult = keycloakCommand.execute(k8sClient, Optional.of(keycloakClient));
        k8sClient.entandoResources().updateStatus(newKeycloakServer, keycloakCommand.getStatus());
    }

}
