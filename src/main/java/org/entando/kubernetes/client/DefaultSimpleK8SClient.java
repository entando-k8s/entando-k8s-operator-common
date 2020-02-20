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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;

public class DefaultSimpleK8SClient implements SimpleK8SClient<EntandoResourceClient> {

    private final KubernetesClient kubernetesClient;
    private ServiceClient serviceClient;
    private PodClient podClient;
    private SecretClient secretClient;
    private EntandoResourceClient entandoResourceClient;
    private DeploymentClient deploymentClient;
    private IngressClient ingressClient;
    private PersistentVolumeClaimClient persistentVolumeClaimClient;
    private ServiceAccountClient serviceAccountClient;

    public DefaultSimpleK8SClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public ServiceClient services() {
        if (this.serviceClient == null) {
            this.serviceClient = new DefaultServiceClient(kubernetesClient);
        }
        return this.serviceClient;
    }

    @Override
    public PodClient pods() {
        if (this.podClient == null) {
            this.podClient = new DefaultPodClient(kubernetesClient);
        }
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        if (this.secretClient == null) {
            this.secretClient = new DefaultSecretClient(kubernetesClient);
        }
        return this.secretClient;
    }

    @Override
    public EntandoResourceClient entandoResources() {
        if (this.entandoResourceClient == null) {

            this.entandoResourceClient = new DefaultEntandoResourceClient(kubernetesClient);
        }
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        if (this.serviceAccountClient == null) {
            this.serviceAccountClient = new DefaultServiceAccountClient(kubernetesClient);
        }
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        if (this.deploymentClient == null) {
            this.deploymentClient = new DefaultDeploymentClient(kubernetesClient);
        }
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        if (this.ingressClient == null) {
            this.ingressClient = new DefaultIngressClient(kubernetesClient);
        }
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        if (this.persistentVolumeClaimClient == null) {
            this.persistentVolumeClaimClient = new DefaultPersistentVolumeClaimClient(kubernetesClient);
        }
        return this.persistentVolumeClaimClient;
    }

}
