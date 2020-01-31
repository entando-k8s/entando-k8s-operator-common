package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.KeycloakClientCreator;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;

public interface SpringBootDeployableContainer extends DbAware, KeycloakAware, IngressingContainer, TlsAware {

    DatabaseSchemaCreationResult getDatabaseSchema();

    @Override
    default int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    default int getMemoryLimitMebibytes() {
        return 1024;
    }

    @Override
    default void addDatabaseConnectionVariables(List<EnvVar> vars) {
        DatabaseSchemaCreationResult databaseSchema = getDatabaseSchema();
        if (databaseSchema != null) {
            vars.add(new EnvVar("DB_VENDOR", databaseSchema.getVendor().getName(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_USERNAME.name(), null, databaseSchema.getUsernameRef()));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_PASSWORD.name(), null, databaseSchema.getPasswordRef()));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_URL.name(), databaseSchema.getJdbcUrl(), null));
            vars.add(
                    new EnvVar(SpringProperty.SPRING_JPA_DATABASE_PLATFORM.name(), databaseSchema.getVendor().getHibernateDialect(), null));
            /*
            TODO: Set SPRING_JPA_PROPERTIES_HIBERNATE_ID_NEW_GENERATOR_MAPPINGS to 'false' if we ever run into issues with ID Generation
            */
            databaseSchema.addAdditionalConfigFromDatabaseSecret(vars);
        }
    }

    @Override
    default void addKeycloakVariables(List<EnvVar> vars) {
        KeycloakConnectionConfig keycloakDeployment = getKeycloakConnectionConfig();
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name(),
                keycloakDeployment.getBaseUrl() + "/realms/entando",
                null));
        String keycloakSecretName = KeycloakClientCreator.keycloakClientSecret(getKeycloakClientConfig());
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name(), null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name(), null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_ID_KEY)));
    }

    enum SpringProperty {
        SPRING_SECURITY_OAUTH_2_CLIENT_PROVIDER_OIDC_ISSUER_URI,
        SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET,
        SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_ID,
        SPRING_DATASOURCE_USERNAME,
        SPRING_DATASOURCE_PASSWORD,
        SPRING_DATASOURCE_URL,
        SPRING_JPA_DATABASE_PLATFORM;
    }

}
