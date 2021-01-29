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

package org.entando.kubernetes.controller.spi.result;

import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;

public abstract class AbstractServiceResult implements ServiceResult {

    protected final Service service;

    protected AbstractServiceResult(Service service) {
        this.service = service;
    }

    @Override
    public String getInternalServiceHostname() {
        return service.getMetadata().getName() + "." + service.getMetadata().getNamespace() + ".svc.cluster.local";
    }

    @Override
    public String getPort() {
        return service.getSpec().getPorts().get(0).getPort().toString();
    }

    @Override
    @SerializeByReference
    public Service getService() {
        return service;
    }

}
