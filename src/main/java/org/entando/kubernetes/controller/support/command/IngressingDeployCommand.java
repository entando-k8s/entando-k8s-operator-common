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

package org.entando.kubernetes.controller.support.command;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.creators.IngressCreator;
import org.entando.kubernetes.model.WebServerStatus;

/**
 * On addition of an Entando CustomResource, the DeployCommand is invoked for every service and database that needs to be deployed.
 */
public class IngressingDeployCommand<T extends ExposedDeploymentResult<T>> extends DeployCommand<T> {

    private final IngressCreator ingressCreator;
    private final IngressingDeployable<T> ingressingDeployable;

    public IngressingDeployCommand(IngressingDeployable<T> deployable) {
        super(deployable);
        this.ingressingDeployable = deployable;
        ingressCreator = new IngressCreator(entandoCustomResource);
    }

    @Override
    protected Ingress maybeCreateIngress(SimpleK8SClient<?> k8sClient) {
        if (ingressingDeployable.getIngressingContainers().isEmpty()) {
            throw new IllegalStateException(
                    deployable.getClass() + " implements IngressingDeployable but has no IngressingContainers.");
        }
        syncIngress(k8sClient, ingressingDeployable);
        return getIngress();
    }

    private void syncIngress(SimpleK8SClient<?> k8sClient, IngressingDeployable<?> ingressingDeployable) {
        if (ingressCreator.requiresDelegatingService(serviceCreator.getService(), ingressingDeployable)) {
            Service newDelegatingService = serviceCreator.newDelegatingService(k8sClient.services(), ingressingDeployable);
            ingressCreator.createIngress(k8sClient.ingresses(), ingressingDeployable, newDelegatingService);
        } else {
            ingressCreator.createIngress(k8sClient.ingresses(), ingressingDeployable, serviceCreator.getService());
        }
        ((WebServerStatus) status).setIngressStatus(ingressCreator.reloadIngress(k8sClient.ingresses()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    public Ingress getIngress() {
        return ingressCreator.getIngress();
    }

}
