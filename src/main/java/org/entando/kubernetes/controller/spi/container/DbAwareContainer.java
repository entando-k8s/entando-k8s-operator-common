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
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DbAwareContainer extends DeployableContainer {

    Logger logger = LoggerFactory.getLogger(DbAwareContainer.class.getName());

    int USERNAME_RANDOM_HASH_LENGTH = 5;

    default Optional<DatabasePopulator> getDatabasePopulator() {
        return Optional.empty();
    }

    List<EnvVar> getDatabaseConnectionVariables();

    List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo();

    static List<DatabaseSchemaConnectionInfo> buildDatabaseSchemaConnectionInfo(
            EntandoCustomResource entandoBaseCustomResource,
            DatabaseConnectionInfo databaseConnectionInfo,
            List<String> schemaQualifiers) {
        return schemaQualifiers.stream()
                .map(schemaQualifier -> {
                    final DbmsVendorConfig dbmsVendor = databaseConnectionInfo.getVendor();
                    String schemaName = NameUtils.databaseCompliantName(
                            entandoBaseCustomResource,
                            schemaQualifier,
                            dbmsVendor
                    );
                    return new DefaultDatabaseSchemaConnectionInfo(databaseConnectionInfo,
                            schemaName,
                            SecretUtils.generateSecret(
                                    entandoBaseCustomResource,
                                    entandoBaseCustomResource.getMetadata().getName() + "-" + schemaQualifier + "-secret",
                                    generateUsername(schemaName, dbmsVendor)
                            )
                    );
                }).collect(Collectors.toList());
    }

    static String generateUsername(String schemaName, DbmsVendorConfig dbmsVendor) {
        int lenWithoutRandom = dbmsVendor.getMaxUsernameLength() - (USERNAME_RANDOM_HASH_LENGTH + 1);

        String username = schemaName;
        if (schemaName.length() > lenWithoutRandom) {
            username = schemaName.substring(0, lenWithoutRandom);
            logger.debug("Database username {} too long: {} supports max length of {}. Truncating to {}",
                    schemaName, dbmsVendor.getName(), dbmsVendor.getMaxUsernameLength(), username);
        }
        return username + "_" + NameUtils.randomNumeric(USERNAME_RANDOM_HASH_LENGTH);
    }

}
