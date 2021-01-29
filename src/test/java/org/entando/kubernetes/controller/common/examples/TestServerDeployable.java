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

import static org.entando.kubernetes.controller.spi.common.SecretUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.database.DatabaseDeploymentResult;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.support.spibase.IngressingDeployableBase;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class TestServerDeployable implements IngressingDeployableBase<SampleExposedDeploymentResult>, DbAwareDeployable,
        Secretive {

    private final EntandoKeycloakServer keycloakServer;
    private final List<DeployableContainer> containers;
    private final DatabaseDeploymentResult databaseServiceResult;
    private final Secret keycloakAdminSecret;

    public TestServerDeployable(EntandoKeycloakServer keycloakServer, DatabaseDeploymentResult databaseServiceResult) {
        this.keycloakServer = keycloakServer;
        this.databaseServiceResult = databaseServiceResult;
        containers = Arrays.asList(new TestServerDeployableContainer(keycloakServer, databaseServiceResult));
        keycloakAdminSecret = generateSecret(this.keycloakServer, TestServerDeployableContainer.secretName(this.keycloakServer),
                "entando_keycloak_admin");
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public EntandoKeycloakServer getCustomResource() {
        return keycloakServer;
    }

    @Override
    public SampleExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new SampleExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.keycloakServer.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

    @Override
    public String getIngressName() {
        return ((EntandoCustomResource) keycloakServer).getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public String getIngressNamespace() {
        return keycloakServer.getMetadata().getNamespace();
    }

    @Override
    public List<Secret> getSecrets() {
        return Arrays.asList(keycloakAdminSecret);
    }

}
