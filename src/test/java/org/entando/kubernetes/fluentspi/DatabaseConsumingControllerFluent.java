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

import java.util.Collections;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import picocli.CommandLine;

/*
Classes to be implemented by the controller provider
 */
@CommandLine.Command()
public class DatabaseConsumingControllerFluent<N extends DatabaseConsumingControllerFluent<N>> extends ControllerFluent<N> {

    private final CapabilityProvider capabilityProvider;
    private DbAwareDeployableFluent<?> deployable;
    private CapabilityRequirement capabilityRequirement;

    public DatabaseConsumingControllerFluent(KubernetesClientForControllers k8sClient,
            DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        super(k8sClient, deploymentProcessor);
        this.capabilityProvider = capabilityProvider;
    }

    public N withDeployable(DeployableFluent<?> deployable) {
        this.deployable = (DbAwareDeployableFluent<?>) deployable;
        return thisAsN();
    }

    public N withDatabaseRequirement(CapabilityRequirement capabilityRequirement) {
        this.capabilityRequirement = capabilityRequirement;
        return thisAsN();

    }

    @Override
    public void run() {
        final EntandoCustomResource resourceToProcess = k8sClient.resolveCustomResourceToProcess(Collections.singleton(supportedClass));
        try {
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.STARTED);
            final ProvidedDatabaseCapability databaseCapability = new ProvidedDatabaseCapability(
                    this.capabilityProvider.provideCapability(resourceToProcess, capabilityRequirement, 30)
            );
            final DefaultExposedDeploymentResult result = deploymentProcessor
                    .processDeployable(deployable.withProvidedDatabase(databaseCapability).withCustomResource(resourceToProcess), 60);
            k8sClient.updateStatus(resourceToProcess, result.getStatus());
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.SUCCESSFUL);
        } catch (Exception e) {
            e.printStackTrace();
            k8sClient.deploymentFailed(resourceToProcess, e, NameUtils.MAIN_QUALIFIER);
            throw new CommandLine.ExecutionException(new CommandLine(this), e.getMessage());
        }

    }
}
