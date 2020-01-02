package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import java.util.List;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.ConfigVariable;
import org.entando.kubernetes.model.DbmsImageVendor;

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
                        getDatabase()).usingSchema(schemaName)
                .buildConnectionString();
    }

    public DbmsImageVendor getVendor() {
        return this.databaseServiceResult.getVendor();
    }

    public String getDatabase() {
        if (getVendor().schemaIsDatabase()) {
            return getSchemaName();
        } else {
            return this.databaseServiceResult.getDatabaseName();
        }
    }

    public void addAdditionalConfigFromDatabaseSecret(List<EnvVar> vars) {
        getVendor().getAdditionalConfig().stream().forEach(cfg -> vars.add(newSecretKeyRef(cfg)));
    }

    protected EnvVar newSecretKeyRef(ConfigVariable cfg) {
        //Point the EnvVar to the externalDb's secret.
        return new EnvVar(cfg.getEnvironmentVariable(), null,
                KubeUtils.secretKeyRef(this.databaseServiceResult.getDatabaseSecretName(), cfg.getConfigKey()));
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
