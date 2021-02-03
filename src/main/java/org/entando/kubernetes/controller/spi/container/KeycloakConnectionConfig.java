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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public class KeycloakConnectionConfig {

    private final Secret adminSecret;
    private final ConfigMap configMap;

    public KeycloakConnectionConfig(Secret adminSecret, ConfigMap configMap) {
        this.adminSecret = adminSecret;
        this.configMap = configMap;
    }

    public String determineBaseUrl() {
        if (EntandoOperatorSpiConfig.forceExternalAccessToKeycloak()) {
            return getExternalBaseUrl();
        } else {
            return getInternalBaseUrl().orElse(getExternalBaseUrl());
        }
    }

    public String getUsername() {
        return decodeSecretValue(SecretUtils.USERNAME_KEY);
    }

    public String getPassword() {
        return decodeSecretValue(SecretUtils.PASSSWORD_KEY);
    }

    public String decodeSecretValue(String key) {
        Optional<String> value = ofNullable(adminSecret.getData())
                .map(data ->
                        ofNullable(data.get(key)).map(s -> new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8))
                )
                .orElse(ofNullable(adminSecret.getStringData()).map(data -> data.get(key)));
        return value.orElse(null);
    }

    public Secret getAdminSecret() {
        return adminSecret;
    }

    public String getExternalBaseUrl() {
        return configMap.getData().get(NameUtils.URL_KEY);
    }

    public Optional<String> getInternalBaseUrl() {
        return Optional.ofNullable(configMap.getData().get(NameUtils.INTERNAL_URL_KEY));
    }
}
