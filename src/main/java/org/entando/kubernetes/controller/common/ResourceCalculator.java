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

package org.entando.kubernetes.controller.common;

import org.entando.kubernetes.controller.spi.DeployableContainer;

public class ResourceCalculator extends LimitAndRequestCalculator {

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
        return applyRequestRatio(getMemoryLimit());
    }

    public String getCpuRequest() {
        return applyRequestRatio(getCpuLimit());
    }

}
