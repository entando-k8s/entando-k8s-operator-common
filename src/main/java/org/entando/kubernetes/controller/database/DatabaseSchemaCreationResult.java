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

package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.api.model.EnvVarSource;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.KubeUtils;

public class DatabaseSchemaCreationResult extends AbstractServiceResult {

    private final DatabaseServiceResult databaseServiceResult;
    private final String schemaSecretName;
    private final String schemaName;

    public DatabaseSchemaCreationResult(DatabaseServiceResult databaseServiceResult, String schemaName, String schemaSecretName) {
        super(databaseServiceResult.getService());
        this.databaseServiceResult = databaseServiceResult;
        this.schemaName = schemaName;
        this.schemaSecretName = schemaSecretName;
    }

    public String getSchemaSecretName() {
        return schemaSecretName;
    }

    public String getJdbcUrl() {
        return getVendor().getConnectionStringBuilder().toHost(getInternalServiceHostname()).onPort(getPort())
                .usingDatabase(
                        getDatabase()).usingSchema(schemaName).usingParameters(this.databaseServiceResult.getJdbcParameters())
                .buildJdbcConnectionString();
    }

    public DbmsVendorStrategy getVendor() {
        return this.databaseServiceResult.getVendor();
    }

    public String getDatabase() {
        if (getVendor().schemaIsDatabase()) {
            return getSchemaName();
        } else {
            return this.databaseServiceResult.getDatabaseName();
        }
    }

    public String getSchemaName() {
        return schemaName;
    }

    public EnvVarSource getPasswordRef() {
        return KubeUtils.secretKeyRef(getSchemaSecretName(), KubeUtils.PASSSWORD_KEY);
    }

    public EnvVarSource getUsernameRef() {
        return KubeUtils.secretKeyRef(getSchemaSecretName(), KubeUtils.USERNAME_KEY);
    }
}
