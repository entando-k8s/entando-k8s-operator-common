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

package org.entando.kubernetes.controller.spi.database;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.model.AbstractServerStatus;

public class DatabaseDeploymentResult extends AbstractServiceResult implements DatabaseServiceResult,
        ServiceDeploymentResult<DatabaseDeploymentResult> {

    private final DbmsDockerVendorStrategy vendor;
    private final String databaseName;
    private final String databaseSecretName;
    private final Pod pod;
    private AbstractServerStatus status;

    public DatabaseDeploymentResult(Service service, DbmsDockerVendorStrategy vendor, String databaseName, String databaseSecretName,
            Pod pod) {
        super(service);
        this.pod = pod;
        this.vendor = vendor;
        this.databaseName = databaseName;
        this.databaseSecretName = databaseSecretName;
    }

    @Override
    public Optional<String> getTablespace() {
        return Optional.empty();
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDatabaseSecretName() {
        return databaseSecretName;
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public DbmsDockerVendorStrategy getVendor() {
        return vendor;
    }

    public boolean hasFailed() {
        return Optional.ofNullable(pod).map(existingPod -> PodResult.of(existingPod).hasFailed()).orElse(false);
    }

    @Override
    public DatabaseDeploymentResult withStatus(AbstractServerStatus status) {
        this.status = status;
        return this;
    }

    public AbstractServerStatus getStatus() {
        return status;
    }

    @Override
    public Pod getPod() {
        return pod;
    }
}
