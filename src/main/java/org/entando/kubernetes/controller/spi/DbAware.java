package org.entando.kubernetes.controller.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;

public interface DbAware extends DeployableContainer {

    List<String> getDbSchemaQualifiers();

    Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas);

}
