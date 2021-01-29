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

package org.entando.kubernetes.controller.support.spibase;

import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public interface PublicIngressingDeployableBase<T extends ExposedDeploymentResult<T>> extends IngressingDeployableBase<T>,
        PublicIngressingDeployable<T> {

    @Override
    @SuppressWarnings("unchecked")
    default String getKeycloakRealmToUse() {
        EntandoBaseCustomResource<KeycloakAwareSpec> ka = (EntandoBaseCustomResource<KeycloakAwareSpec>) getCustomResource();
        return KeycloakName.ofTheRealm(ka.getSpec());
    }

    @Override
    @SuppressWarnings("unchecked")
    default String getPublicClientIdToUse() {
        EntandoBaseCustomResource<KeycloakAwareSpec> ka = (EntandoBaseCustomResource<KeycloakAwareSpec>) getCustomResource();
        return KeycloakName.ofThePublicClient(ka.getSpec());

    }
}
