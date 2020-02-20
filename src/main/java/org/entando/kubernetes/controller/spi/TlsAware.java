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

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.SecretCreator;

public interface TlsAware {

    default void addTlsVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("JAVA_TOOL_OPTIONS",
                null,
                KubeUtils.secretKeyRef(SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, SecretCreator.TRUSTSTORE_SETTINGS_KEY)));

    }
}
