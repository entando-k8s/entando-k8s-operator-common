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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public class BareBonesDeployable<S extends EntandoDeploymentSpec> implements Deployable<BarebonesDeploymentResult, S>, Secretive {

    public static final String MY_SERVICE_ACCOUNT = "my-service-account";
    public static final String NAME_QUALIFIER = "db";
    private final List<DeployableContainer> containers;
    private EntandoBaseCustomResource<S> customResource;
    private DbmsDockerVendorStrategy dbmsVendor = DbmsDockerVendorStrategy.CENTOS_POSTGRESQL;

    public BareBonesDeployable(EntandoBaseCustomResource<S> customResource) {
        this(customResource, new BareBonesContainer());
    }

    public BareBonesDeployable(EntandoBaseCustomResource<S> customResource, DeployableContainer... containers) {
        this.customResource = customResource;
        this.containers = Arrays.asList(containers);
    }

    @Override
    public List<Secret> getSecrets() {
        Secret secret = SecretUtils.generateSecret(customResource, BareBonesContainer.getDatabaseAdminSecretName(),
                dbmsVendor.getDefaultAdminUsername());
        return Arrays.asList(secret);
    }

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return NAME_QUALIFIER;
    }

    @Override
    public EntandoBaseCustomResource<S> getCustomResource() {
        return customResource;
    }

    @Override
    public BarebonesDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new BarebonesDeploymentResult(service, pod);
    }

    @Override
    public String getServiceAccountName() {
        return MY_SERVICE_ACCOUNT;
    }
}
