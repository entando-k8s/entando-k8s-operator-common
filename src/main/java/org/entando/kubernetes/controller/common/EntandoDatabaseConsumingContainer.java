package org.entando.kubernetes.controller.common;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.DbAware;

public abstract class EntandoDatabaseConsumingContainer implements DbAware {

    private static final String PORTDB = "portdb";
    private static final String SERVDB = "servdb";
    private static final String PORTDB_PREFIX = "PORTDB_";
    private static final String SERVDB_PREFIX = "SERVDB_";
    private Map<String, DatabaseSchemaCreationResult> dbSchemas;

    protected DatabasePopulator buildDatabasePopulator() {
        return new EntandoAppDatabasePopulator(this);
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("DB_STARTUP_CHECK", "false", null));
        addEntandoDbConnectionVars(vars, this.dbSchemas.get(PORTDB), PORTDB_PREFIX);
        addEntandoDbConnectionVars(vars, this.dbSchemas.get(SERVDB), SERVDB_PREFIX);
        this.dbSchemas.get(PORTDB).addAdditionalConfigFromDatabaseSecret(vars);
    }

    private void addEntandoDbConnectionVars(List<EnvVar> vars, DatabaseSchemaCreationResult dbDeploymentResult, String varNamePrefix) {
        vars.add(new EnvVar(varNamePrefix + "USERNAME", null,
                KubeUtils.secretKeyRef(dbDeploymentResult.getSchemaSecretName(), KubeUtils.USERNAME_KEY)));
        vars.add(new EnvVar(varNamePrefix + "PASSWORD", null,
                KubeUtils.secretKeyRef(dbDeploymentResult.getSchemaSecretName(), KubeUtils.PASSSWORD_KEY)));
        vars.add(new EnvVar(varNamePrefix + "URL", dbDeploymentResult.getJdbcUrl(), null));
    }

    @Override
    public List<String> getDbSchemaQualifiers() {
        return Arrays.asList(PORTDB, SERVDB);
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas) {
        this.dbSchemas = dbSchemas;
        return Optional.of(buildDatabasePopulator());
    }

    public static class EntandoAppDatabasePopulator implements DatabasePopulator {

        private final EntandoDatabaseConsumingContainer entandoAppDeployableContainer;

        public EntandoAppDatabasePopulator(EntandoDatabaseConsumingContainer entandoAppDeployableContainer) {
            this.entandoAppDeployableContainer = entandoAppDeployableContainer;
        }

        @Override
        public String determineImageToUse() {
            return entandoAppDeployableContainer.determineImageToUse();
        }

        @Override
        public String[] getCommand() {
            return new String[]{"/bin/bash", "-c", "/entando-common/init-db-from-deployment.sh"};
        }

        @Override
        public void addEnvironmentVariables(List<EnvVar> vars) {
            entandoAppDeployableContainer.addEnvironmentVariables(vars);
        }

    }

}
