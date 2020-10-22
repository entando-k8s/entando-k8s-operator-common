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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.database.DatabaseDeployable.VariableInitializer;
import org.entando.kubernetes.controller.spi.HasHealthCommand;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.ServiceBackingContainer;

public class DatabaseContainer implements ServiceBackingContainer, PersistentVolumeAware, HasHealthCommand {

    private final DbmsDockerVendorStrategy dbmsVendor;
    private final String nameQualifier;
    private final VariableInitializer variableInitializer;
    private final Integer portOverride;

    public DatabaseContainer(VariableInitializer variableInitializer, DbmsDockerVendorStrategy dbmsVendor, String nameQualifier,
            Integer portOverride) {
        this.variableInitializer = variableInitializer;
        this.dbmsVendor = dbmsVendor;
        this.nameQualifier = nameQualifier;
        this.portOverride = portOverride;
    }

    @Override
    public String determineImageToUse() {
        return dbmsVendor.getImageName();
    }

    @Override
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public int getPrimaryPort() {
        return ofNullable(portOverride).orElse(dbmsVendor.getPort());
    }

    @Override
    public String getVolumeMountPath() {
        return dbmsVendor.getVolumeMountPath();
    }

    @Override
    public String getHealthCheckCommand() {
        return dbmsVendor.getHealthCheck();
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        variableInitializer.addEnvironmentVariables(vars);
    }

}
