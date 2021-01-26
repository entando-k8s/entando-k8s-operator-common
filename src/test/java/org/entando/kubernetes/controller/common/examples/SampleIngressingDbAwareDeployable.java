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

package org.entando.kubernetes.controller.common.examples;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;

public abstract class SampleIngressingDbAwareDeployable<S extends EntandoIngressingDeploymentSpec> implements
        IngressingDeployable<SampleExposedDeploymentResult, S>, DbAwareDeployable {

    protected final EntandoBaseCustomResource<S> entandoResource;
    protected final List<DeployableContainer> containers;
    protected final DatabaseServiceResult databaseServiceResult;

    public SampleIngressingDbAwareDeployable(EntandoBaseCustomResource<S> entandoResource, DatabaseServiceResult databaseServiceResult) {
        this.entandoResource = entandoResource;
        this.databaseServiceResult = databaseServiceResult;
        this.containers = createContainers(entandoResource);
    }

    protected abstract List<DeployableContainer> createContainers(EntandoBaseCustomResource<S> entandoResource);

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public EntandoBaseCustomResource<S> getCustomResource() {
        return entandoResource;
    }

    @Override
    public SampleExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new SampleExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountName() {
        return "plugin";
    }

    @Override
    public String getIngressName() {
        return NameUtils.standardIngressName(entandoResource);
    }

    @Override
    public String getIngressNamespace() {
        return entandoResource.getMetadata().getNamespace();
    }

}
