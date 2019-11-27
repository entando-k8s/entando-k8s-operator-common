package org.entando.kubernetes.controller.common.example;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.PublicIngressingDeployable;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoApp;

public class KeycloakConsumingDeployable implements PublicIngressingDeployable<ServiceDeploymentResult>, DbAwareDeployable {

    public static final String KEYCLOAK_PUBLIC_CLIENT_ID = "keycloak-public-client-id";
    public static final String TEST_INGRESS_NAMESPACE = "test-ingress-namespace";
    public static final String TEST_INGRESS_NAME = "test-ingress-name";
    public static final String ENTANDO_TEST_IMAGE_6_0_0 = "entando/test-image:6.0.0";
    private final KeycloakConnectionConfig keycloakConnectionConfig;
    private final EntandoApp entandoApp;
    private final List<DeployableContainer> containers = new ArrayList<>();
    private DatabaseServiceResult databaseServiceResult;

    public KeycloakConsumingDeployable(KeycloakConnectionConfig keycloakConnectionConfig, EntandoApp entandoApp,
            DatabaseServiceResult databaseServiceResult) {
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        this.entandoApp = entandoApp;
        this.databaseServiceResult = databaseServiceResult;
    }

    @Override
    public String getPublicKeycloakClientId() {
        return KEYCLOAK_PUBLIC_CLIENT_ID;
    }

    @Override
    public KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return keycloakConnectionConfig;
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
    public String getIngressName() {
        return TEST_INGRESS_NAME;
    }

    @Override
    public String getIngressNamespace() {
        return TEST_INGRESS_NAMESPACE;
    }

    @Override
    public String getNameQualifier() {
        return "testserver";
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return entandoApp;
    }

    @Override
    public ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new ServiceDeploymentResult(service, ingress);
    }
}
