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

package org.entando.kubernetes.controller;

import java.util.Collections;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import picocli.CommandLine;

public class ControllerFluent<N extends ControllerFluent<N>> implements Runnable {

    private final KubernetesClientForControllers k8sClient;
    private final DeploymentProcessor deploymentProcessor;
    private DeployableFluent<?> deployable;
    private Class<? extends EntandoCustomResource> supportedClass;

    public ControllerFluent(KubernetesClientForControllers k8sClient, DeploymentProcessor deploymentProcessor) {
        this.k8sClient = k8sClient;
        this.deploymentProcessor = deploymentProcessor;
    }

    @Override
    public void run() {
        final EntandoCustomResource resourceToProcess = k8sClient.resolveCustomResourceToProcess(Collections.singleton(supportedClass));
        try {
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.STARTED);
            final DefaultExposedDeploymentResult result = deploymentProcessor
                    .processDeployable(deployable.withCustomResource(resourceToProcess), 60);
            k8sClient.updateStatus(resourceToProcess, result.getStatus());
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.SUCCESSFUL);
        } catch (Exception e) {
            e.printStackTrace();
            k8sClient.deploymentFailed(resourceToProcess, e);
            throw new CommandLine.ExecutionException(new CommandLine(this), e.getMessage());
        }

    }

    public N withDeployable(DeployableFluent<?> deployable) {
        this.deployable = deployable;
        return thisAsN();
    }

    public N withSupportedClass(Class<? extends EntandoCustomResource> supportedClass) {
        this.supportedClass = supportedClass;
        return thisAsN();
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }
}
