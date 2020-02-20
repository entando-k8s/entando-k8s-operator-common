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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class InfrastructureConfig {

    private final Secret infrastructureSecret;

    public InfrastructureConfig(Secret infrastructureSecret) {
        this.infrastructureSecret = infrastructureSecret;
    }

    public static String decodeSecretValue(Secret secret, String key) {
        String value = ofNullable(secret.getData()).map(stringStringMap -> stringStringMap.get(key)).orElse(null);
        if (value == null) {
            //If not yet reloaded
            return secret.getStringData().get(key);
        } else {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    public Secret getInfrastructureSecret() {
        return infrastructureSecret;
    }

    public String getK8SInternalServiceUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceInternalUrl");
    }

    public String getUserManagementInternalUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "userManagementInternalUrl");
    }

    public String getK8SExternalServiceUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceExternalUrl");
    }

    public String getUserManagementExternalUrl() {
        return decodeSecretValue(getInfrastructureSecret(), "userManagementExternalUrl");
    }

    public String getK8sServiceClientId() {
        return decodeSecretValue(getInfrastructureSecret(), "entandoK8SServiceClientId");
    }
}
