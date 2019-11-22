package org.entando.kubernetes.controller.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;

public interface DbAware extends DeployableContainer {

    //TODO in future change this into a single string. Each containers 'should' only have one db
    List<String> getDbSchemaQualifiers();

    Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas);

}
