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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.ExternalService;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.creators.DatabasePreparationPodCreator;
import org.entando.kubernetes.controller.support.creators.DeploymentCreator;
import org.entando.kubernetes.controller.support.creators.KeycloakClientCreator;
import org.entando.kubernetes.controller.support.creators.PersistentVolumeClaimCreator;
import org.entando.kubernetes.controller.support.creators.SecretCreator;
import org.entando.kubernetes.controller.support.creators.ServiceAccountCreator;
import org.entando.kubernetes.controller.support.creators.ServiceCreator;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.model.common.InternalServerStatus;

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
    protected EntandoCustomResource entandoCustomResource;
    private Pod pod;
    private final ExecutorService scheduledExecutorService = Executors.newSingleThreadExecutor();
    private Ingress ingress;

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
            status = new ExposedServerStatus();
        } else {
            status = new InternalServerStatus();
        }
        status.setQualifier(deployable.getQualifier().orElse(NameUtils.MAIN_QUALIFIER));
    }

    /**
     * This method is not allowed to throw any exceptions. Any error conditions associated with the execution of this method should be
     * attached to the EntandoCustomResource.
     */
    public T execute(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient potentiallyNullKeycloakClient, int timeoutSeconds) {
        try {
            return scheduledExecutorService.submit(() -> {
                final Optional<ExternalService> externalService = deployable.getExternalService();
                if (externalService.isPresent()) {
                    prepareConnectivityToExternalService(k8sClient, externalService.get());
                } else {
                    deployServiceInternally(k8sClient, potentiallyNullKeycloakClient);
                }
                return deployable.createResult(getDeployment(), getService(), ingress, pod).withStatus(getStatus());
            }).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            this.entandoCustomResource = k8sClient.entandoResources()
                    .deploymentFailed(entandoCustomResource, (Exception) e.getCause(),
                            deployable.getQualifier().orElse(NameUtils.MAIN_QUALIFIER));
        } catch (Exception e) {
            //most likely a timeout exception
            this.entandoCustomResource = k8sClient.entandoResources()
                    .deploymentFailed(entandoCustomResource, e,
                            deployable.getQualifier().orElse(NameUtils.MAIN_QUALIFIER));
        }
        //Note that this line will only be executed if there was an exception
        return deployable.createResult(getDeployment(), getService(), ingress, pod).withStatus(getStatus());
    }

    private void deployServiceInternally(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient potentiallyNullKeycloakClient) {
        Optional<SimpleKeycloakClient> keycloakClient = ofNullable(potentiallyNullKeycloakClient);
        EntandoImageResolver entandoImageResolver = new EntandoImageResolver(
                k8sClient.entandoResources().loadDockerImageInfoConfigMap(),
                deployable.getCustomResource());
        if (persistentVolumeClaimCreator.needsPersistentVolumeClaims(deployable)) {
            createPersistentVolumeClaims(k8sClient);
        }
        secretCreator.createSecrets(k8sClient.secrets(), deployable);
        serviceAccountCreator.prepareServiceAccountAccess(k8sClient.serviceAccounts(), deployable);
        if (shouldCreateService(deployable)) {
            createService(k8sClient);
        }
        this.ingress = maybeCreateIngress(k8sClient);
        if (keycloakClientCreator.requiresKeycloakClients(deployable)) {
            keycloakClientCreator.createKeycloakClients(
                    k8sClient.secrets(),
                    keycloakClient.orElseThrow(IllegalStateException::new),
                    deployable,
                    ofNullable(ingress));
        }
        if (deployable instanceof DbAwareDeployable && ((DbAwareDeployable<?>) deployable).isExpectingDatabaseSchemas()) {
            prepareDbSchemas(k8sClient, entandoImageResolver, (DbAwareDeployable<?>) deployable);
        }
        createDeployment(k8sClient, entandoImageResolver);
        waitForPod(k8sClient);
        getStatus().finish();
    }

    private void prepareConnectivityToExternalService(SimpleK8SClient<?> k8sClient, ExternalService externalService) {
        if (externalService.getCreateDelegateService()) {
            serviceCreator.createExternalService(k8sClient, externalService);
            getStatus().setServiceName(getService().getMetadata().getName());
        }
        //For ExternalService that does not require an internal service, we do nothing
        getStatus().finish();
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
                .waitForPod(entandoCustomResource.getMetadata().getNamespace(), LabelNames.DEPLOYMENT.getName(), resolveName(deployable));
        status.putPodPhase(pod.getMetadata().getName(), pod.getStatus().getPhase());
        entandoCustomResource = k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
        if (PodResult.of(pod).hasFailed()) {
            throw new EntandoControllerException(pod,
                    format("Deployment failed. Please inspect the logs of the pod %s/%s",
                            pod.getMetadata().getNamespace(),
                            pod.getMetadata().getName()));

        }
    }

    private String resolveName(Deployable<T> deployable) {
        return entandoCustomResource.getMetadata().getName() + deployable.getQualifier().map(s -> "-" + s).orElse("");
    }

    private void createDeployment(SimpleK8SClient<?> k8sClient, EntandoImageResolver entandoImageResolver) {
        deploymentCreator.createDeployment(entandoImageResolver, k8sClient.deployments(), deployable);
        status.setDeploymentName(deploymentCreator.getDeployment().getMetadata().getName());
        entandoCustomResource = k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createService(SimpleK8SClient<?> k8sClient) {
        serviceCreator.createService(k8sClient.services(), deployable);
        status.setServiceName(serviceCreator.getService().getMetadata().getName());
        entandoCustomResource = k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void createPersistentVolumeClaims(SimpleK8SClient<?> k8sClient) {
        //NB!!! it seems there is some confusion in K8S when a patch is done without any changes.
        //K8Sseems to increment either the resourceVersion or generation or both and then
        //subsequent updates fail
        persistentVolumeClaimCreator.createPersistentVolumeClaimsFor(k8sClient.persistentVolumeClaims(), deployable);
        persistentVolumeClaimCreator.reloadPersistentVolumeClaims(k8sClient.persistentVolumeClaims()).forEach(
                persistentVolumeClaim -> status.putPersistentVolumeClaimPhase(persistentVolumeClaim.getMetadata().getName(),
                        ofNullable(persistentVolumeClaim.getStatus()).map(PersistentVolumeClaimStatus::getPhase).orElse("Pending"))
        );
        entandoCustomResource = k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
    }

    private void prepareDbSchemas(SimpleK8SClient<?> k8sClient, EntandoImageResolver entandoImageResolver,
            DbAwareDeployable<?> dbAwareDeployable) {
        Pod completedPod = databasePreparationJobCreator.runToCompletion(k8sClient, dbAwareDeployable, entandoImageResolver);
        status.putPodPhase(completedPod.getMetadata().getName(), completedPod.getStatus().getPhase());
        entandoCustomResource = k8sClient.entandoResources().updateStatus(entandoCustomResource, status);
        PodResult podResult = PodResult.of(completedPod);
        if (podResult.hasFailed()) {
            throw new EntandoControllerException(completedPod,
                    format("Database preparation failed. Please inspect the logs of the pod %s/%s",
                            completedPod.getMetadata().getNamespace(), completedPod.getMetadata().getName()));
        } else if (EntandoOperatorConfig.garbageCollectSuccessfullyCompletedPods()) {
            k8sClient.pods().deletePod(completedPod);
        }
    }

    public Service getService() {
        return serviceCreator.getService();
    }

    public AbstractServerStatus getStatus() {
        return status;
    }

    public Deployment getDeployment() {
        return deploymentCreator.getDeployment();
    }

}
