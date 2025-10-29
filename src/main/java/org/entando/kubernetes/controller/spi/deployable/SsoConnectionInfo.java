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

package org.entando.kubernetes.controller.spi.deployable;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;

public interface SsoConnectionInfo {

    String getBaseUrlToUse();

    default String getUsername() {
        return decodeSecretValue(SecretUtils.USERNAME_KEY);
    }

    default String getPassword() {
        return decodeSecretValue(SecretUtils.PASSSWORD_KEY);
    }

    default String decodeSecretValue(String key) {
        Secret adminSecret = this.getAdminSecret();
        return Optional.ofNullable(adminSecret.getData())
                .map(data -> data.get(key))
                .filter(StringUtils::isNotEmpty)
                .map(s -> new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8))
                .orElseGet(() -> Optional.ofNullable(adminSecret.getStringData())
                        .map(data -> data.get(key))
                        .orElse(null)
                );
    }

    Secret getAdminSecret();

    String getExternalBaseUrl();

    Optional<String> getDefaultRealm();

    Optional<String> getInternalBaseUrl();
}
