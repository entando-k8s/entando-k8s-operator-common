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
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.database.DatabaseDeploymentResult;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;

public class KeycloakConsumingDeployable implements PublicIngressingDeployable<SampleExposedDeploymentResult, EntandoAppSpec>,
        DbAwareDeployable {

    public static final String TEST_INGRESS_NAMESPACE = "test-ingress-namespace";
    public static final String TEST_INGRESS_NAME = "test-ingress-name";
    public static final String ENTANDO_TEST_IMAGE_6_0_0 = "entando/test-image:6.0.0";
    private final KeycloakConnectionConfig keycloakConnectionConfig;
    private final EntandoApp entandoApp;
    private final List<DeployableContainer> containers = new ArrayList<>();
    private DatabaseDeploymentResult databaseServiceResult;

    public KeycloakConsumingDeployable(KeycloakConnectionConfig keycloakConnectionConfig, EntandoApp entandoApp,
            DatabaseDeploymentResult databaseServiceResult) {
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        this.entandoApp = entandoApp;
        this.databaseServiceResult = databaseServiceResult;
    }

    @Override
    public KeycloakConnectionConfig getKeycloakDeploymentResult() {
        return keycloakConnectionConfig;
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getIngressName() {
        return TEST_INGRESS_NAME;
    }

    @Override
    public String getIngressNamespace() {
        return TEST_INGRESS_NAMESPACE;
    }

    @Override
    public String getNameQualifier() {
        return "testserver";
    }

    @Override
    public EntandoApp getCustomResource() {
        return entandoApp;
    }

    @Override
    public SampleExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new SampleExposedDeploymentResult(pod, service, ingress);
    }
}
