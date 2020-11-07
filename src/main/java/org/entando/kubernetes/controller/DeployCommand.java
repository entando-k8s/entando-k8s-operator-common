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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.creators.DatabasePreparationPodCreator;
import org.entando.kubernetes.controller.creators.DeploymentCreator;
import org.entando.kubernetes.controller.creators.KeycloakClientCreator;
import org.entando.kubernetes.controller.creators.PersistentVolumeClaimCreator;
import org.entando.kubernetes.controller.creators.SecretCreator;
import org.entando.kubernetes.controller.creators.ServiceAccountCreator;
import org.entando.kubernetes.controller.creators.ServiceCreator;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.ServiceDeploymentResult;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbServerStatus;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.WebServerStatus;

public class DeployCommand<T extends ServiceDeploymentResult, S extends EntandoDeploymentSpec> {

    public static final String DEPLOYMENT_LABEL_NAME = "deployment";
    protected final Deployable<T, S> deployable;
    protected final PersistentVolumeClaimCreator<S> persistentVolumeClaimCreator;
    protected final ServiceCreator<S> serviceCreator;
    protected final DeploymentCreator<S> deploymentCreator;
    protected final SecretCreator<S> secretCreator;
    protected final DatabasePreparationPodCreator<S> databasePreparationJobCreator;
    protected final KeycloakClientCreator keycloakClientCreator;
    protected final ServiceAccountCreator<S> serviceAccountCreator;
    protected final AbstractServerStatus status;
    protected final EntandoBaseCustomResource<S> entandoCustomResource;
    private Pod pod;

    public DeployCommand(Deployable<T, S> deployable) {
        this.deployable = deployable;
        this.entandoCustomResource = deployable.getCustomResource();
        persistentVolumeClaimCreator = new PersistentVolumeClaimCreator<>(entandoCustomResource);
        serviceCreator = new ServiceCreator<>(entandoCustomResource);
        deploymentCreator = new DeploymentCreator<>(entandoCustomResource);
        secretCreator = new SecretCreator<>(entandoCustomResource);
        databasePreparationJobCreator = new DatabasePreparationPodCreator<>(entandoCustomResource);
        keycloakClientCreator = new KeycloakClientCreator(entandoCustomResource);
        serviceAccountCreator = new ServiceAccountCreator<>(entandoCustomResource);
        if (deployable instanceof IngressingDeployable) {
            status = new WebServerStatus();
        } else {
            status = new DbServerStatus();
        }
        status.setQualifier(deployable.getNameQualifier());
    }

    @SuppressWarnings("unchecked")
    public T execute(SimpleK8SClient<?> k8sClient, Optional<SimpleKeycloakClient> keycloakClient) {
        EntandoImageResolver entandoImageResolver = new EntandoImageResolver(
                k8sClient.secrets().loadControllerConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()));
        if (deployable instanceof DbAwareDeployable && ((DbAwareDeployable) deployable).hasContainersExpectingSchemas()) {
            prepareDbSchemas(k8sClient, entandoImageResolver, (DbAwareDeployable) deployable);
        }
        if (persistentVolumeClaimCreator.needsPersistentVolumeClaaims(deployable)) {
            //NB!!! it seems there is some confusion in K8S when a patch is done without any changes.
            //K8Sseems to increment either the resourceVersion or generation or both and then
            //subsequent updates fail
            createPersistentVolumeClaims(k8sClient);
        }
        secretCreator.createSecrets(k8sClient.secrets(), deployable);
        serviceAccountCreator.prepareServiceAccountAccess(k8sClient.serviceAccounts(), deployable);
        if (shouldCreateService(deployable)) {
            createService(k8sClient);
        }
        Ingress ingress = maybeCreateIngress(k8sClient);
        if (keycloakClientCreator.requiresKeycloakClients(deployable)) {
            keycloakClientCreator.createKeycloakClients(
                    k8sClient.secrets(),
                    keycloakClient.orElseThrow(IllegalStateException::new),
                    deployable,
                    Optional.ofNullable(ingress));
        }
        createDeployment(k8sClient, entandoImageResolver);
        waitForPod(k8sClient);
        if (status.hasFailed()) {
            throw new EntandoControllerException("Creation of Kubernetes resources has failed");
        }
        return (T) deployable.createResult(getDeployment(), getService(), ingress, getPod()).withStatus(getStatus());
    }

    @SuppressWarnings("java:S1172")//because this parameter is required for the subclass
    protected Ingress maybeCreateIngress(SimpleK8SClient<?> k8sClient) {
        return null;
    }

    private boolean shouldCreateService(Deployable<T, S> deployable) {
        return deployable.getContainers().stream().anyMatch(ServiceBackingContainer.class::isInstance);
    }

    private void waitForPod(SimpleK8SClient<?> k8sClient) {
        pod = k8sClient.pods()
                .waitForPod(entandoCustomResource.getMetadata().getNamespace(), DEPLOYMENT_LABEL_NAME, resolveName(deployable));
        status.setPodStatus(pod.getStatus());
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private String resolveName(Deployable<T, S> deployable) {
        return entandoCustomResource.getMetadata().getName() + "-" + deployable.getNameQualifier();
    }

    private void createDeployment(SimpleK8SClient<?> k8sClient, EntandoImageResolver entandoImageResolver) {
        deploymentCreator.createDeployment(entandoImageResolver, k8sClient.deployments(), deployable);
        status.setDeploymentStatus(deploymentCreator.reloadDeployment(k8sClient.deployments()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createService(SimpleK8SClient<?> k8sClient) {
        serviceCreator.createService(k8sClient.services(), deployable);
        status.setServiceStatus(serviceCreator.reloadPrimaryService(k8sClient.services()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createPersistentVolumeClaims(SimpleK8SClient<?> k8sClient) {
        persistentVolumeClaimCreator.createPersistentVolumeClaimsFor(k8sClient.persistentVolumeClaims(), deployable);
        List<PersistentVolumeClaimStatus> statuses = persistentVolumeClaimCreator
                .reloadPersistentVolumeClaims(k8sClient.persistentVolumeClaims());
        status.setPersistentVolumeClaimStatuses(statuses);
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void prepareDbSchemas(SimpleK8SClient<?> k8sClient, EntandoImageResolver entandoImageResolver,
            DbAwareDeployable dbAwareDeployable) {
        Pod completedPod = databasePreparationJobCreator.runToCompletion(k8sClient, dbAwareDeployable, entandoImageResolver);
        status.setInitPodStatus(completedPod.getStatus());
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
        PodResult podResult = PodResult.of(completedPod);
        if (podResult.hasFailed()) {
            throw new EntandoControllerException("Could not init database schemas");
        }
    }

    public Service getService() {
        return serviceCreator.getService();
    }

    public AbstractServerStatus getStatus() {
        return status;
    }

    public Pod getPod() {
        return pod;
    }

    public Deployment getDeployment() {
        return deploymentCreator.getDeployment();
    }

}
