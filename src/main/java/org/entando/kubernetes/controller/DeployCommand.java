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
import org.entando.kubernetes.controller.creators.IngressCreator;
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
import org.entando.kubernetes.controller.spi.ServiceResult;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbServerStatus;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.WebServerStatus;

/**
 * On addition of an Entando CustomResource, the DeployCommand is invoked for every service and database that needs to be deployed.
 */
public class DeployCommand<T extends ServiceResult> {

    public static final String DEPLOYMENT_LABEL_NAME = "deployment";
    private static final String DEFAULT = "default";
    private final Deployable<T> deployable;
    private final PersistentVolumeClaimCreator persistentVolumeClaimCreator;
    private final ServiceCreator serviceCreator;
    private final DeploymentCreator deploymentCreator;
    private final IngressCreator ingressCreator;
    private final SecretCreator secretCreator;
    private final DatabasePreparationPodCreator databasePreparationJobCreator;
    private final KeycloakClientCreator keycloakClientCreator;
    private final ServiceAccountCreator serviceAccountCreator;
    private final AbstractServerStatus status;
    private final EntandoCustomResource entandoCustomResource;
    private Pod pod;

    public DeployCommand(Deployable<T> deployable) {
        entandoCustomResource = deployable.getCustomResource();
        serviceAccountCreator = new ServiceAccountCreator(entandoCustomResource);
        persistentVolumeClaimCreator = new PersistentVolumeClaimCreator(entandoCustomResource);
        serviceCreator = new ServiceCreator(entandoCustomResource);
        ingressCreator = new IngressCreator(entandoCustomResource);
        secretCreator = new SecretCreator(entandoCustomResource);
        databasePreparationJobCreator = new DatabasePreparationPodCreator(entandoCustomResource);
        deploymentCreator = new DeploymentCreator(entandoCustomResource);
        this.deployable = deployable;
        if (deployable instanceof IngressingDeployable) {
            status = new WebServerStatus();
        } else {
            status = new DbServerStatus();
        }
        status.setQualifier(deployable.getNameQualifier());
        keycloakClientCreator = new KeycloakClientCreator(entandoCustomResource);
    }

    public T execute(SimpleK8SClient k8sClient, Optional<SimpleKeycloakClient> keycloakClient) {
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
        if (shouldCreateServiceAccount()) {
            serviceAccountCreator.prepareServiceAccount(k8sClient.serviceAccounts(), deployable);
        }
        if (shouldCreateService(deployable)) {
            createService(k8sClient);
        }
        if (deployable instanceof IngressingDeployable) {
            if (((IngressingDeployable) this.deployable).getIngressingContainers().isEmpty()) {
                throw new IllegalStateException(
                        deployable.getClass() + " implements IngressingDeployable but has no IngressingContainers.");
            }
            syncIngress(k8sClient, (IngressingDeployable) this.deployable);
        }
        if (keycloakClientCreator.requiresKeycloakClients(deployable)) {
            keycloakClientCreator.createKeycloakClients(
                    k8sClient.secrets(),
                    keycloakClient.orElseThrow(IllegalStateException::new),
                    deployable,
                    Optional.ofNullable(getIngress()));
        }
        createDeployment(k8sClient, entandoImageResolver);
        waitForPod(k8sClient);
        if (status.hasFailed()) {
            throw new EntandoControllerException("Creation of Kubernetes resources has failed");
        }
        return deployable.createResult(getDeployment(), getService(), getIngress(), getPod());
    }

    private boolean shouldCreateService(Deployable<T> deployable) {
        return deployable.getContainers().stream().anyMatch(ServiceBackingContainer.class::isInstance);
    }

    private boolean shouldCreateServiceAccount() {
        return !DEFAULT.equals(deployable.determineServiceAccountName())
                && EntandoOperatorConfig.getOperatorSecurityMode() == SecurityMode.LENIENT;
    }

    private void waitForPod(SimpleK8SClient k8sClient) {
        pod = k8sClient.pods()
                .waitForPod(entandoCustomResource.getMetadata().getNamespace(), DEPLOYMENT_LABEL_NAME, resolveName(deployable));
        status.setPodStatus(pod.getStatus());
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private String resolveName(Deployable<T> deployable) {
        return entandoCustomResource.getMetadata().getName() + "-" + deployable.getNameQualifier();
    }

    private void createDeployment(SimpleK8SClient k8sClient, EntandoImageResolver entandoImageResolver) {
        deploymentCreator.createDeployment(entandoImageResolver, k8sClient.deployments(), deployable);
        status.setDeploymentStatus(deploymentCreator.reloadDeployment(k8sClient.deployments()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void syncIngress(SimpleK8SClient k8sClient, IngressingDeployable<?> ingressingContainer) {
        if (ingressCreator.requiresDelegatingService(serviceCreator.getService(), ingressingContainer)) {
            Service newDelegatingService = serviceCreator.newDelegatingService(k8sClient.services(), ingressingContainer);
            ingressCreator.createIngress(k8sClient.ingresses(), ingressingContainer, newDelegatingService);
        } else {
            ingressCreator.createIngress(k8sClient.ingresses(), ingressingContainer, serviceCreator.getService());
        }
        ((WebServerStatus) status).setIngressStatus(ingressCreator.reloadIngress(k8sClient.ingresses()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createService(SimpleK8SClient k8sClient) {
        serviceCreator.createService(k8sClient.services(), deployable);
        status.setServiceStatus(serviceCreator.reloadPrimaryService(k8sClient.services()));
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createPersistentVolumeClaims(SimpleK8SClient k8sClient) {
        persistentVolumeClaimCreator.createPersistentVolumeClaimsFor(k8sClient.persistentVolumeClaims(), deployable);
        List<PersistentVolumeClaimStatus> statuses = persistentVolumeClaimCreator
                .reloadPersistentVolumeClaims(k8sClient.persistentVolumeClaims());
        status.setPersistentVolumeClaimStatuses(statuses);
        k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void prepareDbSchemas(SimpleK8SClient k8sClient, EntandoImageResolver entandoImageResolver,
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

    public Ingress getIngress() {
        return ingressCreator.getIngress();
    }

}
