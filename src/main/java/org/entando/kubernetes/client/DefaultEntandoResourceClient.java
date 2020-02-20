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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoResourceOperationsRegistry;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class DefaultEntandoResourceClient implements EntandoResourceClient, PatchableClient {

    private final KubernetesClient client;
    private final EntandoResourceOperationsRegistry entandoResourceRegistry;

    public DefaultEntandoResourceClient(KubernetesClient client) {
        this.client = client;
        entandoResourceRegistry = new EntandoResourceOperationsRegistry(client);
    }

    @Override
    public KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource) {
        String secretName = resource.getKeycloakSecretToUse().orElse(EntandoOperatorConfig.getDefaultKeycloakSecretName());
        return new KeycloakConnectionSecret(this.client.secrets().withName(secretName).fromServer().get());
    }

    @Override
    public InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource) {
        String secretName = EntandoOperatorConfig.getEntandoInfrastructureSecretName();
        return new InfrastructureConfig(this.client.secrets().withName(secretName).get());
    }

    @Override
    public ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource) {
        return new ServiceDeploymentResult(
                loadService(resource, standardServiceName(resource)),
                loadIngress(resource, standardIngressName(resource)));
    }

    public String standardServiceName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-server-service";
    }

    public String standardIngressName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + KubeUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        List<EntandoDatabaseService> externalDatabaseList = getOperations(EntandoDatabaseService.class)
                .inNamespace(resource.getMetadata().getNamespace()).list().getItems();
        return externalDatabaseList.stream().filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms() == vendor)
                .findFirst().map(externalDatabase ->
                        new ExternalDatabaseDeployment(loadService(resource, externalDatabase.getMetadata().getName() + "-service"), null,
                                externalDatabase));
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status) {
        getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .edit()
                .withStatus(status)
                .done();
    }

    protected Supplier<IllegalStateException> notFound(String kind, String namespace, String name) {
        return () -> new IllegalStateException(format("Could not find the %s '%s' in the namespace %s", kind, name, namespace));
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return ofNullable(getOperations(clzz).inNamespace(resourceNamespace)
                .withName(resourceName).get()).orElseThrow(() -> notFound(clzz.getSimpleName(), resourceNamespace, resourceName).get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        Class<T> type = (Class<T>) r.getClass();
        return createOrPatch(r, r, this.getOperations(type));

    }

    @SuppressWarnings("unchecked")
    private <T extends EntandoCustomResource, D extends DoneableEntandoCustomResource<D, T>> CustomResourceOperationsImpl<T,
            CustomResourceList<T>, D> getOperations(Class<T> c) {
        return entandoResourceRegistry.getOperations(c);
    }

    @Override
    public void updatePhase(EntandoCustomResource customResource, EntandoDeploymentPhase phase) {
        getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .edit().withPhase(phase).done();

    }

    @Override
    public void deploymentFailed(EntandoCustomResource customResource, Exception reason) {
        Optional<AbstractServerStatus> currentServerStatus = getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName()).get().getStatus().findCurrentServerStatus();
        if (currentServerStatus.isPresent()) {
            AbstractServerStatus newStatus = currentServerStatus.get();
            newStatus.finishWith(new EntandoControllerFailureBuilder().withException(reason).build());
            getOperations(customResource.getClass())
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName())
                    .edit().withStatus(newStatus).withPhase(EntandoDeploymentPhase.FAILED).done();
        } else {
            getOperations(customResource.getClass())
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName())
                    .edit().withPhase(EntandoDeploymentPhase.FAILED).done();

        }
    }

    private Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    private Ingress loadIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    protected Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
