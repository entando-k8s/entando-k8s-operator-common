package org.entando.kubernetes.controller.common.examples.springboot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class SampleSpringBootDeployableContainer<T extends EntandoBaseCustomResource> implements SpringBootDeployableContainer {

    public static final String MY_IMAGE = "entando/entando-k8s-service";
    public static final String MY_WEB_CONTEXT = "/my-context";
    private final T customResource;
    private final KeycloakConnectionConfig keycloakConnectionConfig;
    private Map<String, DatabaseSchemaCreationResult> dbSchemas;

    public SampleSpringBootDeployableContainer(T customResource,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        this.customResource = customResource;
        this.keycloakConnectionConfig = keycloakConnectionConfig;
    }

    @Override
    public String getWebContextPath() {
        return MY_WEB_CONTEXT;
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of("/healthcheck");
    }

    @Override
    public String determineImageToUse() {
        return MY_IMAGE;
    }

    @Override
    public String getNameQualifier() {
        return KubeUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPort() {
        return 8080;
    }

    @Override
    public KeycloakConnectionConfig getKeycloakConnectionConfig() {
        return this.keycloakConnectionConfig;
    }

    @Override
    public KeycloakClientConfig getKeycloakClientConfig() {
        return new KeycloakClientConfig(KubeUtils.ENTANDO_KEYCLOAK_REALM,
                customResource.getMetadata().getName() + "-" + getNameQualifier(),
                customResource.getMetadata().getName() + "-" + getNameQualifier());
    }

    @Override
    public List<String> getDbSchemaQualifiers() {
        return Arrays.asList(getNameQualifier() + "db");
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas) {
        this.dbSchemas = dbSchemas;
        return Optional.empty();
    }

    @Override
    public DatabaseSchemaCreationResult getDatabaseSchema() {
        return this.dbSchemas.get(getNameQualifier() + "db");
    }
}
