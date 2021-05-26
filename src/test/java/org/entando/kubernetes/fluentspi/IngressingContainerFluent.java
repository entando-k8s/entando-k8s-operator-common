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

import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.TrustStoreAwareContainer;

public class IngressingContainerFluent<N extends IngressingContainerFluent<N>> extends DeployableContainerFluent<N> implements
        IngressingContainer, TrustStoreAwareContainer {

    private String webContextPath;
    private String healthCheckPath;

    @Override
    public String getWebContextPath() {
        return this.webContextPath;
    }

    public N withWebContextPath(String webContextPath) {
        this.webContextPath = webContextPath;
        return thisAsN();
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.ofNullable(this.healthCheckPath);
    }

    public N withHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
        return thisAsN();
    }
}
