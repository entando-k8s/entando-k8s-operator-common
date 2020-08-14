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

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;

public class KeycloakConnectionSecret implements KeycloakConnectionConfig {

    private final Secret adminSecret;

    public KeycloakConnectionSecret(Secret adminSecret) {
        this.adminSecret = adminSecret;
    }

    @Override
    public Secret getAdminSecret() {
        return adminSecret;
    }

    @Override
    public String getBaseUrl() {
        return decodeSecretValue(KubeUtils.URL_KEY);
    }

    @Override
    public String getInternalBaseUrl() {
        return Optional.ofNullable(decodeSecretValue(KubeUtils.INTERNAL_URL_KEY)).orElse(getBaseUrl());
    }
}
