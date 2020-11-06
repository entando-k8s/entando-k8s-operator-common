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

package org.entando.kubernetes.controller.database;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class DatabaseDeployable<S extends EntandoDeploymentSpec> implements
        Deployable<DatabaseDeploymentResult, S>, Secretive {

    private final DbmsDockerVendorStrategy dbmsVendor;
    private final EntandoBaseCustomResource<S> customResource;
    private final String nameQualifier;
    protected List<DeployableContainer> containers;

    public DatabaseDeployable(DbmsDockerVendorStrategy dbmsVendor, EntandoBaseCustomResource<S> customResource, String nameQualifier,
            Integer portOverride) {
        this.dbmsVendor = dbmsVendor;
        this.customResource = customResource;
        this.nameQualifier = nameQualifier;
        this.containers = Arrays
                .asList(new DatabaseContainer(buildVariableInitializer(dbmsVendor), dbmsVendor, nameQualifier, portOverride));
    }

    private VariableInitializer buildVariableInitializer(DbmsDockerVendorStrategy vendorStrategy) {
        switch (vendorStrategy) {
            case MYSQL:
                return vars ->
                        //No DB creation. Dbs are created during schema creation
                        vars.add(new EnvVar("MYSQL_ROOT_PASSWORD", null,
                                KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));
            case POSTGRESQL:
                return vars -> {
                    vars.add(new EnvVar("POSTGRESQL_DATABASE", getDatabaseName(), null));
                    // This username will not be used, as we will be creating schema/user pairs,
                    // but without it the DB isn't created.
                    vars.add(new EnvVar("POSTGRESQL_USER", getDatabaseName() + "_user", null));
                    vars.add(new EnvVar("POSTGRESQL_PASSWORD", null,
                            KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));
                    vars.add(new EnvVar("POSTGRESQL_ADMIN_PASSWORD", null,
                            KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));

                };
            default:
                throw new IllegalStateException(
                        format("The DBMS %s not supported for containerized deployments", vendorStrategy.getName()));
        }
    }

    @Override
    public Optional<Long> getFileSystemUserAndGroupId() {
        return dbmsVendor.getFileSystemUserGroupid();
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public EntandoBaseCustomResource<S> getCustomResource() {
        return customResource;
    }

    @Override
    public DatabaseDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DatabaseDeploymentResult(service, dbmsVendor, getDatabaseName(), getDatabaseAdminSecretName(), pod);
    }

    @Override
    public List<Secret> buildSecrets() {
        Secret secret = KubeUtils.generateSecret(customResource, getDatabaseAdminSecretName(),
                dbmsVendor.getDefaultAdminUsername());
        return Arrays.asList(secret);
    }

    protected String getDatabaseAdminSecretName() {
        return ExternalDatabaseDeployment.adminSecretName(customResource, getNameQualifier());
    }

    protected String getDatabaseName() {
        return ExternalDatabaseDeployment.databaseName(customResource, getNameQualifier());
    }

    interface VariableInitializer {

        void addEnvironmentVariables(List<EnvVar> vars);
    }

}
