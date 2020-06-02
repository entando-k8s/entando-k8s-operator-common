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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class DatabaseServiceResult extends AbstractServiceResult {

    private final DbmsDockerVendorStrategy vendor;
    private final String databaseName;
    private final String databaseSecretName;
    private String tablespace;
    private Pod pod;
    private Map<String, String> databaseParameters;

    public DatabaseServiceResult(Service service, DbmsDockerVendorStrategy vendor, String databaseName, String databaseSecretName,
            Pod pod) {
        this(service, vendor, databaseName, databaseSecretName);
        this.pod = pod;
        this.databaseParameters = Collections.emptyMap();
    }

    private DatabaseServiceResult(Service service, DbmsDockerVendorStrategy vendor, String databaseName, String databaseSecretName) {
        super(service);
        this.vendor = vendor;
        this.databaseName = databaseName;
        this.databaseSecretName = databaseSecretName;
    }

    public DatabaseServiceResult(Service service, EntandoDatabaseService databaseService) {
        this(service,
                DbmsDockerVendorStrategy.forVendor(databaseService.getSpec().getDbms()),
                databaseService.getSpec().getDatabaseName(),
                databaseService.getSpec().getSecretName());
        this.databaseParameters = databaseService.getSpec().getJdbcParameters();
        this.tablespace = databaseService.getSpec().getTablespace().orElse(null);
    }

    public Optional<String> getTablespace() {
        return Optional.ofNullable(tablespace);
    }

    public Map<String, String> getJdbcParameters() {
        return databaseParameters;
    }

    public String getDatabaseSecretName() {
        return databaseSecretName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public DbmsDockerVendorStrategy getVendor() {
        return vendor;
    }

    public boolean hasFailed() {
        return Optional.ofNullable(pod).map(existingPod -> PodResult.of(existingPod).hasFailed()).orElse(false);
    }

}
