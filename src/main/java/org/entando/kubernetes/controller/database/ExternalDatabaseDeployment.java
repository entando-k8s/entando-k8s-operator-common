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

import static java.lang.String.format;
import static org.entando.kubernetes.controller.KubeUtils.snakeCaseOf;

import io.fabric8.kubernetes.api.model.Service;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class ExternalDatabaseDeployment extends AbstractServiceResult implements DatabaseServiceResult {

    public static final String NAME_QUALIFIER = "db";
    public static final String DATABASE_SERVICE_SUFFIX = format("-%s-%s", NAME_QUALIFIER, KubeUtils.DEFAULT_SERVICE_SUFFIX);

    protected final EntandoDatabaseService externalDatabase;

    public ExternalDatabaseDeployment(Service service, EntandoDatabaseService externalDatabase) {
        super(service);
        this.externalDatabase = externalDatabase;
    }

    public static String adminSecretName(EntandoBaseCustomResource<?> resource, String nameQualifier) {
        return format("%s-%s-admin-secret", resource.getMetadata().getName(), nameQualifier);
    }

    public static String databaseName(EntandoBaseCustomResource<?> resource, String nameQualifier) {
        return snakeCaseOf(resource.getMetadata().getName() + "_" + nameQualifier);

    }

    public static String serviceName(EntandoDatabaseService externalDatabase) {
        return externalDatabase.getMetadata().getName() + DATABASE_SERVICE_SUFFIX;
    }

    public EntandoDatabaseService getEntandoDatabaseService() {
        return externalDatabase;
    }

    @Override
    public String getDatabaseSecretName() {
        return getEntandoDatabaseService().getSpec().getSecretName().orElse(adminSecretName(externalDatabase, NAME_QUALIFIER));
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        return getEntandoDatabaseService().getSpec().getJdbcParameters();
    }

    @Override
    public String getDatabaseName() {
        return getEntandoDatabaseService().getSpec().getDatabaseName().orElse(databaseName(externalDatabase, NAME_QUALIFIER));
    }

    @Override
    public DbmsDockerVendorStrategy getVendor() {
        return DbmsDockerVendorStrategy.forVendor(getEntandoDatabaseService().getSpec().getDbms());
    }

    @Override
    public Optional<String> getTablespace() {
        return Optional.empty();
    }
}
