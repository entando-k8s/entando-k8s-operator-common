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

package org.entando.kubernetes.controller.spi.container;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public interface DbAware extends DeployableContainer {

    default Optional<DatabasePopulator> getDatabasePopulator() {
        return Optional.empty();
    }

    List<EnvVar> getDatabaseConnectionVariables();

    List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo();

    static List<DatabaseSchemaConnectionInfo> buildDatabaseSchemaConnectionInfo(
            EntandoBaseCustomResource<? extends EntandoDeploymentSpec> entandoBaseCustomResource,
            DatabaseServiceResult databaseServiceResult,
            List<String> schemaQualifiers) {
        /**
         * String used to distinguish resources in special cases like name shortening.
         */
        String identifierSuffix = SecretUtils.randomAlphanumeric(3);
        return schemaQualifiers.stream().map(schemaQualifier -> {
            String schemaName = NameUtils.snakeCaseOf(entandoBaseCustomResource.getMetadata().getName()) + "_" + schemaQualifier;
            if (schemaName.length() > databaseServiceResult.getVendor().getVendorConfig().getMaxNameLength()) {
                schemaName = schemaName.substring(0, databaseServiceResult.getVendor().getVendorConfig().getMaxNameLength() - 3)
                        + identifierSuffix;
            }
            return new DatabaseSchemaConnectionInfo(databaseServiceResult,
                    schemaName,
                    SecretUtils.generateSecret(entandoBaseCustomResource,
                            entandoBaseCustomResource.getMetadata().getName() + "-" + schemaQualifier + "-secret",
                            schemaName)
            );
        }).collect(Collectors.toList());
    }

}
