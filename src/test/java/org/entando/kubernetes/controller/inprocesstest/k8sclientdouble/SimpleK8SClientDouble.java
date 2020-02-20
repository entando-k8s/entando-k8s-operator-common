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

package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.mockito.Mockito;

public class SimpleK8SClientDouble extends AbstractK8SClientDouble implements SimpleK8SClient<EntandoResourceClientDouble> {

    private final ServiceClient serviceClient = Mockito.spy(new ServiceClientDouble(getNamespaces()));
    private final PersistentVolumeClaimClient persistentVolumeClaimClient = Mockito
            .spy(new PersistentVolumentClaimClientDouble(
                    getNamespaces()));
    private final IngressClient ingressClient = Mockito.spy(new IngressClientDouble(getNamespaces()));
    private final DeploymentClient deploymentClient = Mockito.spy(new DeploymentClientDouble(getNamespaces()));
    private final SecretClient secretClient = Mockito.spy(new SecretClientDouble(getNamespaces()));
    private final EntandoResourceClientDouble entandoResourceClient = Mockito.spy(new EntandoResourceClientDouble(getNamespaces()));
    private final PodClient podClient = Mockito.spy(new PodClientDouble(getNamespaces()));
    private final ServiceAccountClientDouble serviceAccountClient = Mockito.spy(new ServiceAccountClientDouble(getNamespaces()));

    @Override
    public ServiceClient services() {
        return this.serviceClient;
    }

    @Override
    public PodClient pods() {
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        return this.secretClient;
    }

    @Override
    public EntandoResourceClientDouble entandoResources() {
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        return this.persistentVolumeClaimClient;
    }

}
