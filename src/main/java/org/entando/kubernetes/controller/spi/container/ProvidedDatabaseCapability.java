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

package org.entando.kubernetes.controller.spi.container;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;

public class ProvidedDatabaseCapability extends AbstractServiceResult implements DatabaseServiceResult {

    public static final String DATABASE_NAME_PARAMETER = "databaseName";
    public static final String DBMS_VENDOR_PARAMETER = "dbmsVendor";
    public static final String ADMIN_SECRET_NAME_PARAMETER = "adminSecretName";
    public static final String JDBC_PARAMETER_PREFIX = "jdbc-";

    private final ProvidedCapability databaseCapability;

    public ProvidedDatabaseCapability(CapabilityProvisioningResult capabilityProvisioningResult) {
        super(capabilityProvisioningResult.getService());
        this.databaseCapability = capabilityProvisioningResult.getProvidedCapability();
    }

    @Override
    public String getDatabaseSecretName() {
        return findStatus().getDerivedDeploymentParameters().get(ADMIN_SECRET_NAME_PARAMETER);
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        Map<String, String> result = new HashMap<>();
        databaseCapability.getSpec().getCapabilityParameters().forEach((key, value) -> {
            if (key.startsWith(JDBC_PARAMETER_PREFIX)) {
                result.put(key.substring(JDBC_PARAMETER_PREFIX.length()), value);
            }
        });
        return result;
    }

    @Override
    public String getDatabaseName() {
        return findStatus().getDerivedDeploymentParameters().get(DATABASE_NAME_PARAMETER);
    }

    @Override
    public DbmsDockerVendorStrategy getVendor() {
        return DbmsDockerVendorStrategy.valueOf(findStatus().getDerivedDeploymentParameters().get(DBMS_VENDOR_PARAMETER));
    }

    private AbstractServerStatus findStatus() {
        return databaseCapability.getStatus().findCurrentServerStatus().orElseThrow(IllegalStateException::new);
    }

    @Override
    public Optional<String> getTablespace() {
        return Optional.ofNullable(databaseCapability.getSpec().getCapabilityParameters().get("tablespace"));
    }
}
