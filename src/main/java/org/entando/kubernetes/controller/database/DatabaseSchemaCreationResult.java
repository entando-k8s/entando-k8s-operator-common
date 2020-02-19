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
