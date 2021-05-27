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

package org.entando.kubernetes.fluentspi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.model.common.AbstractServerStatus;

public class TestResourceDeploymentResult extends AbstractServiceResult implements ServiceDeploymentResult<TestResourceDeploymentResult> {

    private final String vendor;
    private final String databaseName;
    private AbstractServerStatus status;

    protected TestResourceDeploymentResult(Service service, String vendor, String databaseName, String adminSecretName) {
        super(service, adminSecretName);
        this.vendor = vendor;
        this.databaseName = databaseName;
    }

    @Override
    public TestResourceDeploymentResult withStatus(AbstractServerStatus status) {
        this.status = status;
        status.setAdminSecretName(adminSecretName);
        status.putDerivedDeploymentParameter(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, databaseName);
        status.putDerivedDeploymentParameter(ProvidedDatabaseCapability.DBMS_VENDOR_PARAMETER, vendor);
        return this;
    }

    @Override
    public AbstractServerStatus getStatus() {
        return status;
    }

    @Override
    public Pod getPod() {
        return null;
    }
}
