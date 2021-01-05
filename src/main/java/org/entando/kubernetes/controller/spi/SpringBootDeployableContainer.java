/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.KeycloakName;
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
    default List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
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
        }
        return vars;
    }

    @Override
    default List<EnvVar> getKeycloakVariables() {
        List<EnvVar> vars = KeycloakAware.super.getKeycloakVariables();
        KeycloakConnectionConfig keycloakDeployment = getKeycloakConnectionConfig();
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name(),
                keycloakDeployment.getExternalBaseUrl() + "/realms/" + determineRealm(),
                null));
        String keycloakSecretName = KeycloakName.forTheClientSecret(getKeycloakClientConfig());
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name(), null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name(), null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }

    enum SpringProperty {
        SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI,
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET,
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID,
        SPRING_DATASOURCE_USERNAME,
        SPRING_DATASOURCE_PASSWORD,
        SPRING_DATASOURCE_URL,
        SPRING_JPA_DATABASE_PLATFORM;
    }

}
