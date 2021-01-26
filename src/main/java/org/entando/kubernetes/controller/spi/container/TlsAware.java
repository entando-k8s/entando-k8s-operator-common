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

package org.entando.kubernetes.controller.spi.container;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public interface TlsAware {

    String DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME = "entando-default-ca-secret";
    String TRUSTSTORE_SETTINGS_KEY = "TRUSTSTORE_SETTINGS";

    default List<EnvVar> getTlsVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("JAVA_TOOL_OPTIONS",
                null,
                SecretUtils.secretKeyRef(DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, TRUSTSTORE_SETTINGS_KEY)));
        return vars;

    }
}
