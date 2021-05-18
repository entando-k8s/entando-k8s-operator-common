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

import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;

public class DefaultDatabaseSchemaConnectionInfo implements DatabaseSchemaConnectionInfo {

    private final DatabaseServiceResult databaseServiceResult;
    private final String schemaName;
    private final Secret schemaSecret;

    public DefaultDatabaseSchemaConnectionInfo(DatabaseServiceResult databaseServiceResult, String schemaName, Secret schemaSecret) {
        this.databaseServiceResult = databaseServiceResult;
        this.schemaName = schemaName;
        this.schemaSecret = schemaSecret;
    }

    @Override
    public String getSchemaSecretName() {
        return schemaSecret.getMetadata().getName();
    }

    @Override
    public String getJdbcUrl() {
        return getDatabaseServiceResult().getVendor().getConnectionStringBuilder().toHost(databaseServiceResult.getInternalServiceHostname())
                .onPort(databaseServiceResult.getPort())
                .usingDatabase(
                        getDatabaseServiceResult().getDatabaseName()).usingSchema(schemaName)
                .usingParameters(this.databaseServiceResult.getJdbcParameters())
                .buildJdbcConnectionString();
    }

    public String getDatabase() {
        if (getDatabaseServiceResult().getVendor().schemaIsDatabase()) {
            return getSchemaName();
        } else {
            return this.databaseServiceResult.getDatabaseName();
        }
    }

    public DatabaseServiceResult getDatabaseServiceResult() {
        return databaseServiceResult;
    }

    @Override
    public String getSchemaName() {
        return schemaName;
    }

    @Override
    public EnvVarSource getPasswordRef() {
        return SecretUtils.secretKeyRef(getSchemaSecretName(), SecretUtils.PASSSWORD_KEY);
    }

    @Override
    public EnvVarSource getUsernameRef() {
        return SecretUtils.secretKeyRef(getSchemaSecretName(), SecretUtils.USERNAME_KEY);
    }

    public Secret getSchemaSecret() {
        return this.schemaSecret;
    }

}
