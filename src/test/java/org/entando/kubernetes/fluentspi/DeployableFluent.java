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

package org.entando.kubernetes.fluentspi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public abstract class DeployableFluent<N extends DeployableFluent<N>> implements Deployable<DefaultExposedDeploymentResult> {

    private List<DeployableContainer> containers = new ArrayList<>();
    private String qualifier;
    private EntandoCustomResource customResource;
    private String serviceAccountToUse = "default";

    @Override
    public List<DeployableContainer> getContainers() {
        return this.containers;
    }

    public N withContainer(DeployableContainer container) {
        this.containers.add(container);
        return thisAsN();
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }

    @Override
    public Optional<String> getQualifier() {
        return Optional.ofNullable(this.qualifier);
    }

    public N withQualifier(String qualifier) {
        this.qualifier = qualifier;
        return thisAsN();
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return this.customResource;
    }

    public N withCustomResource(EntandoCustomResource customResource) {
        this.customResource = customResource;
        return thisAsN();
    }

    @Override
    public DefaultExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DefaultExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.serviceAccountToUse;
    }

    public N withServiceAccountToUse(String serviceAccountToUse) {
        this.serviceAccountToUse = serviceAccountToUse;
        return thisAsN();
    }

    public <C extends NestedDeployableContainerFluent<C>> NestedDeployableContainerFluent<C> withNewContainer() {
        return new NestedDeployableContainerFluent<>();
    }

    public class NestedDeployableContainerFluent<C extends DeployableContainerFluent<C>> extends DeployableContainerFluent<C> {

        public N done() {
            DeployableFluent.this.withContainer(this);
            return DeployableFluent.this.thisAsN();
        }
    }

}
