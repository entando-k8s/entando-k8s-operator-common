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

package org.entando.kubernetes.controller.spi.capability;

public enum StandardCapabilityImplementation implements NamedEnum {
    KEYCLOAK(StandardCapability.SSO),
    REDHAT_SSO(StandardCapability.SSO),
    MYSQL(StandardCapability.DBMS),
    POSTGRESQL(StandardCapability.DBMS),
    ORACLE(StandardCapability.DBMS);

    private StandardCapability capability;

    StandardCapabilityImplementation(StandardCapability capability) {
        this.capability = capability;
    }

    public StandardCapability getCapability() {
        return capability;
    }
}
