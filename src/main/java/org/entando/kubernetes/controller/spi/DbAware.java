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

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;

public interface DbAware extends DeployableContainer {

    List<String> getDbSchemaQualifiers();

    Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> dbSchemas);

    List<EnvVar> getDatabaseConnectionVariables();
}
