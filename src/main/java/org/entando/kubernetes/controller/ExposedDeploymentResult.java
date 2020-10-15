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

package org.entando.kubernetes.controller;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.spi.ServiceDeploymentResult;
import org.entando.kubernetes.model.AbstractServerStatus;

public class ExposedDeploymentResult<T extends ExposedDeploymentResult> extends ExposedService implements ServiceDeploymentResult<T> {

    private AbstractServerStatus status;
    private Pod pod;

    public ExposedDeploymentResult(Pod pod, Service service, Ingress ingress) {
        super(service, ingress);
        this.pod = pod;
    }

    @Override
    public T withStatus(AbstractServerStatus status) {
        this.status = status;
        return thisAsT();
    }

    @SuppressWarnings("gene")
    private T thisAsT() {
        return (T) this;
    }

    @Override
    public AbstractServerStatus getStatus() {
        return status;
    }

    @Override
    public Pod getPod() {
        return pod;
    }
}
