package org.entando.kubernetes.controller.common.examples;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.DbAware;

public class DbAwareKeycloakContainer extends MinimalKeycloakContainer implements DbAware {

    @Override
    public List<String> getDbSchemaQualifiers() {
        return Arrays.asList("db");
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas) {
        return Optional.empty();
    }

    @Override
    public void addDatabaseConnectionVariables(List<EnvVar> envVars) {

    }
}
