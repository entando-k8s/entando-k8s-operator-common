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
import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.KubeUtils;

public class DatabaseSchemaConnectionInfo extends AbstractServiceResult {

    private final DatabaseServiceResult databaseServiceResult;
    private final String schemaName;
    private final Secret schemaSecret;

    public DatabaseSchemaConnectionInfo(DatabaseServiceResult databaseServiceResult, String schemaName, Secret schemaSecret) {
        super(databaseServiceResult.getService());
        this.databaseServiceResult = databaseServiceResult;
        this.schemaName = schemaName;
        this.schemaSecret = schemaSecret;
    }

    public String getSchemaSecretName() {
        return schemaSecret.getMetadata().getName();
    }

    public String getJdbcUrl() {
        return getVendor().getVendorConfig().getConnectionStringBuilder().toHost(getInternalServiceHostname()).onPort(getPort())
                .usingDatabase(
                        getDatabase()).usingSchema(schemaName).usingParameters(this.databaseServiceResult.getJdbcParameters())
                .buildJdbcConnectionString();
    }

    public DbmsDockerVendorStrategy getVendor() {
        return this.databaseServiceResult.getVendor();
    }

    public String getDatabase() {
        if (getVendor().getVendorConfig().schemaIsDatabase()) {
            return getSchemaName();
        } else {
            return this.databaseServiceResult.getDatabaseName();
        }
    }

    public DatabaseServiceResult getDatabaseServiceResult() {
        return databaseServiceResult;
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

    public Secret getSchemaSecret() {
        return this.schemaSecret;
    }

}
