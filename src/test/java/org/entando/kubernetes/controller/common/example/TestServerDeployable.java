package org.entando.kubernetes.controller.common.example;

import static org.entando.kubernetes.controller.KubeUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class TestServerDeployable implements IngressingDeployable<ServiceDeploymentResult>, DbAwareDeployable, Secretive {

    private final EntandoKeycloakServer keycloakServer;
    private final List<DeployableContainer> containers;
    private final DatabaseServiceResult databaseServiceResult;
    private final Secret keycloakAdminSecret;

    public TestServerDeployable(EntandoKeycloakServer keycloakServer, DatabaseServiceResult databaseServiceResult) {
        this.keycloakServer = keycloakServer;
        this.databaseServiceResult = databaseServiceResult;
        containers = Arrays.asList(new TestServerDeployableContainer(keycloakServer));
        keycloakAdminSecret = generateSecret(this.keycloakServer, TestServerDeployableContainer.secretName(this.keycloakServer),
                "entando_keycloak_admin");
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public DatabaseServiceResult getDatabaseServiceResult() {
        return databaseServiceResult;
    }

    @Override
    public String getNameQualifier() {
        return KubeUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return keycloakServer;
    }

    @Override
    public ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new ServiceDeploymentResult(service, ingress);
    }

    @Override
    public String getIngressName() {
        return KubeUtils.standardIngressName(keycloakServer);
    }

    @Override
    public String getIngressNamespace() {
        return keycloakServer.getMetadata().getNamespace();
    }

    @Override
    public List<Secret> buildSecrets() {
        return Arrays.asList(keycloakAdminSecret);
    }

}
