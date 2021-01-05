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

package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public interface SecretBasedCredentials {

    default String decodeSecretValue(String key) {
        Optional<String> value = ofNullable(getSecret().getData())
                .map(data ->
                        ofNullable(data.get(key)).map(s -> new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8))
                )
                .orElse(ofNullable(getSecret().getStringData()).map(data -> data.get(key)));
        return value.orElse(null);
    }

    default String getUsername() {
        return decodeSecretValue(KubeUtils.USERNAME_KEY);
    }

    default String getPassword() {
        return decodeSecretValue(KubeUtils.PASSSWORD_KEY);
    }

    Secret getSecret();
}
