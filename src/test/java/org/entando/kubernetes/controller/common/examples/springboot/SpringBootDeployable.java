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

package org.entando.kubernetes.controller.common.examples.springboot;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.common.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.spibase.IngressingDeployableBase;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public class SpringBootDeployable<S extends KeycloakAwareSpec> implements
        IngressingDeployableBase<SampleExposedDeploymentResult>,
        DbAwareDeployable<SampleExposedDeploymentResult> {

    private final EntandoBaseCustomResource<S> customResource;
    private final DeployableContainer container;

    public SpringBootDeployable(EntandoBaseCustomResource<S> customResource,
            KeycloakConnectionConfig keycloakConnectionConfig,
            DatabaseServiceResult databaseServiceResult) {
        this.customResource = customResource;
        container = new SampleSpringBootDeployableContainer<>(customResource, keycloakConnectionConfig, databaseServiceResult);
    }

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    @Override
    public List<DeployableContainer> getContainers() {
        return Arrays.asList(container);
    }

    @Override
    public String getIngressName() {
        return customResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public String getIngressNamespace() {
        return customResource.getMetadata().getNamespace();
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public EntandoBaseCustomResource<S> getCustomResource() {
        return customResource;
    }

    @Override
    public SampleExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new SampleExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.customResource.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

}
