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

package org.entando.kubernetes.controller.spi.examples;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class MinimalKeycloakContainer implements IngressingContainer {

    public static final String KCP = "7UTcVFN0HzaPQmV4bJDE";//RandomStringUtils.randomAlphanumeric(20);
    protected EntandoKeycloakServer keycloakServer;

    public MinimalKeycloakContainer(EntandoKeycloakServer keycloakServer) {
        this.keycloakServer = keycloakServer;
    }

    public int getMemoryLimitMebibytes() {
        return 512;
    }

    public int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    public String determineImageToUse() {
        return "entando/entando-keycloak:6.0.0-SNAPSHOT";
    }

    @Override
    public String getNameQualifier() {
        return "server";
    }

    @Override
    public int getPrimaryPort() {
        return 8080;
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("DB_VENDOR", "h2", null));
        vars.add(new EnvVar("KEYCLOAK_USER", "test-admin", null));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", KCP, null));
        return vars;
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }
}
