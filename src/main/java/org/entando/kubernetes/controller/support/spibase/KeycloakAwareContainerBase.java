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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.entando.kubernetes.controller.spi.common.ForInternalUseOnly;
import org.entando.kubernetes.controller.spi.container.KeycloakAwareContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public interface KeycloakAwareContainerBase extends KeycloakAwareContainer {

    @JsonIgnore
    @ForInternalUseOnly
    KeycloakAwareSpec getKeycloakAwareSpec();

    default String getKeycloakRealmToUse() {
        KeycloakAwareSpec keycloakAwareSpec = getKeycloakAwareSpec();
        return KeycloakName.ofTheRealm(keycloakAwareSpec);

    }

    default String getPublicClientIdToUse() {
        KeycloakAwareSpec keycloakAwareSpec = getKeycloakAwareSpec();
        return KeycloakName.ofThePublicClient(keycloakAwareSpec);
    }

}
