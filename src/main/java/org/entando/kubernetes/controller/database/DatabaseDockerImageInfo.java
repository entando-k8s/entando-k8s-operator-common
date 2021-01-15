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

import java.util.Optional;
import org.entando.kubernetes.controller.common.DockerImageInfo;

public class DatabaseDockerImageInfo implements DockerImageInfo {

    private final DbmsDockerVendorStrategy dbmsVendorDockerStrategy;

    public DatabaseDockerImageInfo(DbmsDockerVendorStrategy dbmsVendorDockerStrategy) {
        this.dbmsVendorDockerStrategy = dbmsVendorDockerStrategy;
    }

    @Override
    public String getRepository() {
        return dbmsVendorDockerStrategy.getImageRepository();
    }

    @Override
    public Optional<String> getOrganization() {
        return Optional.of(dbmsVendorDockerStrategy.getOrganization());
    }

    @Override
    public Optional<String> getRegistryHost() {
        return Optional.of(dbmsVendorDockerStrategy.getRegistry());
    }

    @Override
    public Optional<Integer> getRegistryPort() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getVersion() {
        return Optional.of("latest");//because these are vendor provided images and the versions of both the DB and OS is in the image name
    }
}
