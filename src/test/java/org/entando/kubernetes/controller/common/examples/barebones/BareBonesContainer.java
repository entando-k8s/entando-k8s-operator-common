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

package org.entando.kubernetes.controller.common.examples.barebones;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.KubernetesPermission;

public class BareBonesContainer implements DeployableContainer {

    public static final String NAME_QUALIFIER = "my-db";
    public static final String DATABASE_NAME = "my_db";
    public static final String DATABASE_USER = "my_user";
    public static final String DATABASE_SECRET_NAME = "my-db-secret";
    public static final int MEMORY_LIMIT = 256;
    public static final int CPU_LIMIT = 800;
    private final DbmsDockerVendorStrategy dbmsVendor = DbmsDockerVendorStrategy.POSTGRESQL;

    public static String getDatabaseAdminSecretName() {
        return DATABASE_SECRET_NAME;
    }

    @Override
    public String determineImageToUse() {
        return dbmsVendor.getImageName();
    }

    @Override
    public String getNameQualifier() {
        return NAME_QUALIFIER;
    }

    @Override
    public int getPort() {
        return dbmsVendor.getPort();
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("POSTGRESQL_DATABASE", DATABASE_NAME, null));
        // This username will not be used, as we will be creating schema/user pairs,
        // but without it the DB isn't created.
        vars.add(new EnvVar("POSTGRESQL_USER", DATABASE_USER, null));
        vars.add(new EnvVar("POSTGRESQL_PASSWORD", null,
                KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));
        vars.add(new EnvVar("POSTGRESQL_ADMIN_PASSWORD", null,
                KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return MEMORY_LIMIT;
    }

    @Override
    public int getCpuLimitMillicores() {
        return CPU_LIMIT;
    }

    @Override
    public List<String> getNamesOfSecretsToMount() {
        return Arrays.asList(getDatabaseAdminSecretName());
    }

    @Override
    public List<KubernetesPermission> getKubernetesPermissions() {
        return Arrays.asList(new KubernetesPermission("entando.org", "EntandoApp", "get", "create"));
    }
}
