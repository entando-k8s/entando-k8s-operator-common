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

package org.entando.kubernetes.controller.support.creators;

import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceCalculator extends LimitAndRequestCalculator {

    private static final Logger log = LoggerFactory.getLogger(ResourceCalculator.class);

    private final DeployableContainer container;

    public ResourceCalculator(DeployableContainer container) {
        this.container = container;
    }

    public String getMemoryLimit() {
        return container.getMemoryLimitMebibytes() + "Mi";
    }

    public String getCpuLimit() {
        return container.getCpuLimitMillicores() + "m";
    }

    public String getMemoryRequest() {
        String memReq = applyRequestRatio(getMemoryLimit());
        log.trace("configurable getMemoryRequest:'{}'", memReq);
        return memReq;
    }

    public String getCpuRequest() {
        return applyRequestRatio(getCpuLimit());
    }

}
