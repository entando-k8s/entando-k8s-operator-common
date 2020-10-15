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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class ExternalDatabaseDeployment extends AbstractServiceResult implements DatabaseServiceResult {

    protected final Endpoints endpoints;
    protected final EntandoDatabaseService externalDatabase;

    public ExternalDatabaseDeployment(Service service, Endpoints endpoints, EntandoDatabaseService externalDatabase) {
        super(service);
        this.endpoints = endpoints;
        this.externalDatabase = externalDatabase;
    }

    public EntandoDatabaseService getEntandoDatabaseService() {
        return externalDatabase;
    }

    @Override
    public String getDatabaseSecretName() {
        return getEntandoDatabaseService().getSpec().getSecretName();
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        return getEntandoDatabaseService().getSpec().getJdbcParameters();
    }

    @Override
    public String getDatabaseName() {
        return getEntandoDatabaseService().getSpec().getDatabaseName();
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
