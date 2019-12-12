package org.entando.kubernetes.controller;

import static org.entando.kubernetes.controller.KubeUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public class SampleServerDeployable<T extends EntandoBaseCustomResource> implements PublicIngressingDeployable<ServiceDeploymentResult>,
        DbAwareDeployable, Secretive {

    public static final String SAMPLE_PUBLIC_CLIENT = "sample-public-client";
    private final T entandoResource;
    private final List<DeployableContainer> containers;
    private final DatabaseServiceResult databaseServiceResult;
    private final Secret sampleSecret;
    private KeycloakConnectionConfig keycloakConnectionConfig;

    public SampleServerDeployable(T entandoResource, DatabaseServiceResult databaseServiceResult,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        this.entandoResource = entandoResource;
        this.databaseServiceResult = databaseServiceResult;
        containers = createContainers(entandoResource);
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        sampleSecret = generateSecret(this.entandoResource, secretName(this.entandoResource),
                "entando_keycloak_admin");
    }

    public static <T extends EntandoBaseCustomResource> String secretName(T resource) {
        return resource.getMetadata().getName() + "-admin-secret";
    }

    protected List<DeployableContainer> createContainers(T entandoResource) {
        return Arrays.asList(new SampleDeployableContainer<>(entandoResource));
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
        return entandoResource;
    }

    @Override
    public ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new ServiceDeploymentResult(service, ingress);
    }

    @Override
    public String getServiceAccountName() {
        return "plugin";
    }

    @Override
    public String getIngressName() {
        return KubeUtils.standardIngressName(entandoResource);
    }

    @Override
    public String getIngressNamespace() {
        return entandoResource.getMetadata().getNamespace();
    }

    @Override
    public List<Secret> buildSecrets() {
        return Arrays.asList(sampleSecret);
    }

    @Override
    public String getPublicKeycloakClientId() {
        return SAMPLE_PUBLIC_CLIENT;
    }

    @Override
    public KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return keycloakConnectionConfig;
    }
}
