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
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.common.DockerImageInfo;
import org.entando.kubernetes.controller.database.DatabaseDeployable.VariableInitializer;
import org.entando.kubernetes.controller.spi.HasHealthCommand;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.ServiceBackingContainer;

public class DatabaseContainer implements ServiceBackingContainer, PersistentVolumeAware, HasHealthCommand {

    private final DbmsDockerVendorStrategy dbmsVendorDockerStrategy;
    private final String nameQualifier;
    private final VariableInitializer variableInitializer;
    private final Integer portOverride;

    public DatabaseContainer(VariableInitializer variableInitializer, DbmsDockerVendorStrategy dbmsVendorDockerStrategy,
            String nameQualifier,
            Integer portOverride) {
        this.variableInitializer = variableInitializer;
        this.dbmsVendorDockerStrategy = dbmsVendorDockerStrategy;
        this.nameQualifier = nameQualifier;
        this.portOverride = portOverride;
    }

    @Override
    public DockerImageInfo getDockerImageInfo() {
        return new DatabaseDockerImageInfo(dbmsVendorDockerStrategy);
    }

    @Override
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public int getPrimaryPort() {
        return ofNullable(portOverride).orElse(dbmsVendorDockerStrategy.getPort());
    }

    @Override
    public String getVolumeMountPath() {
        return dbmsVendorDockerStrategy.getVolumeMountPath();
    }

    @Override
    public String getHealthCheckCommand() {
        return dbmsVendorDockerStrategy.getHealthCheck();
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        variableInitializer.addEnvironmentVariables(vars);
        return vars;
    }

}
