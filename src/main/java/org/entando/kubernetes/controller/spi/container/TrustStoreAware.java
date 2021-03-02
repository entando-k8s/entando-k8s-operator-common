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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public interface TrustStoreAware {

    String DEFAULT_TRUSTSTORE_SECRET = "entando-default-truststore";
    String CERT_SECRET_MOUNT_ROOT = "/etc/entando/certs";
    SecretToMount DEFAULT_TRUSTSTORE_SECRET_TO_MOUNT = new SecretToMount(DEFAULT_TRUSTSTORE_SECRET,
            CERT_SECRET_MOUNT_ROOT + File.separatorChar + DEFAULT_TRUSTSTORE_SECRET);
    String TRUSTSTORE_SETTINGS_KEY = "TRUSTSTORE_SETTINGS";
    String TRUST_STORE_FILE = "store.jks";
    String TRUST_STORE_PATH = DEFAULT_TRUSTSTORE_SECRET_TO_MOUNT.getMountPath() + File.separatorChar + TRUST_STORE_FILE;
    String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";

    default List<EnvVar> getTrustStoreVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar(JAVA_TOOL_OPTIONS,
                null,
                SecretUtils.secretKeyRef(DEFAULT_TRUSTSTORE_SECRET, TRUSTSTORE_SETTINGS_KEY)));
        return vars;
    }
}
