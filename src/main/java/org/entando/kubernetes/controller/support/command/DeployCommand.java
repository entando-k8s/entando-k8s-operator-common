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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.controller.EntandoControllerException;
import org.entando.kubernetes.controller.support.creators.DatabasePreparationPodCreator;
import org.entando.kubernetes.controller.support.creators.DeploymentCreator;
import org.entando.kubernetes.controller.support.creators.KeycloakClientCreator;
import org.entando.kubernetes.controller.support.creators.PersistentVolumeClaimCreator;
import org.entando.kubernetes.controller.support.creators.SecretCreator;
import org.entando.kubernetes.controller.support.creators.ServiceAccountCreator;
import org.entando.kubernetes.controller.support.creators.ServiceCreator;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbServerStatus;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.WebServerStatus;

public class DeployCommand<T extends ServiceDeploymentResult<T>> {

    protected final Deployable<T> deployable;
    protected final PersistentVolumeClaimCreator persistentVolumeClaimCreator;
    protected final ServiceCreator serviceCreator;
    protected final DeploymentCreator deploymentCreator;
    protected final SecretCreator secretCreator;
    protected final DatabasePreparationPodCreator databasePreparationJobCreator;
    protected final KeycloakClientCreator keycloakClientCreator;
    protected final ServiceAccountCreator serviceAccountCreator;
    protected final AbstractServerStatus status;
    protected final EntandoCustomResource entandoCustomResource;
    private Pod pod;

    public DeployCommand(Deployable<T> deployable) {
        this.deployable = deployable;
        this.entandoCustomResource = deployable.getCustomResource();
        persistentVolumeClaimCreator = new PersistentVolumeClaimCreator(entandoCustomResource);
        serviceCreator = new ServiceCreator(entandoCustomResource);
        deploymentCreator = new DeploymentCreator(entandoCustomResource);
        secretCreator = new SecretCreator(entandoCustomResource);
        databasePreparationJobCreator = new DatabasePreparationPodCreator(entandoCustomResource);
        keycloakClientCreator = new KeycloakClientCreator(entandoCustomResource);
        serviceAccountCreator = new ServiceAccountCreator(entandoCustomResource);
        if (deployable instanceof IngressingDeployable) {
            status = new WebServerStatus();
        } else {
            status = new DbServerStatus();
        }
        status.setQualifier(deployable.getNameQualifier());
    }

    public T execute(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient potentiallyNullKeycloakClient) {
        Optional<SimpleKeycloakClient> keycloakClient = Optional.ofNullable(potentiallyNullKeycloakClient);
        EntandoImageResolver entandoImageResolver = new EntandoImageResolver(
                k8sClient.secrets().loadControllerConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()));
        if (deployable instanceof DbAwareDeployable && ((DbAwareDeployable) deployable).isExpectingDatabaseSchemas()) {
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
        return deployable.createResult(getDeployment(), getService(), ingress, getPod()).withStatus(getStatus());
    }

    @SuppressWarnings("java:S1172")//because this parameter is required for the subclass
    protected Ingress maybeCreateIngress(SimpleK8SClient<?> k8sClient) {
        return null;
    }

    private boolean shouldCreateService(Deployable<T> deployable) {
        return deployable.getContainers().stream().anyMatch(ServiceBackingContainer.class::isInstance);
    }

    private void waitForPod(SimpleK8SClient<?> k8sClient) {
        pod = k8sClient.pods()
                .waitForPod(entandoCustomResource.getMetadata().getNamespace(), KubeUtils.DEPLOYMENT_LABEL_NAME, resolveName(deployable));
        status.setPodStatus(pod.getStatus());
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private String resolveName(Deployable<T> deployable) {
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
