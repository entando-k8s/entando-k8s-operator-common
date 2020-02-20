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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import java.util.Optional;
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
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public class EntandoResourceClientDouble extends AbstractK8SClientDouble implements EntandoResourceClient {

    public EntandoResourceClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    public void putEntandoApp(EntandoApp entandoApp) {
        createOrPatchEntandoResource(entandoApp);
    }

    public void putEntandoPlugin(EntandoPlugin entandoPlugin) {
        createOrPatchEntandoResource(entandoPlugin);
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        this.getNamespace(r).getCustomResources((Class<T>) r.getClass()).put(r.getMetadata().getName(), r);
        return r;
    }

    public void putEntandoDatabaseService(EntandoDatabaseService externalDatabase) {
        createOrPatchEntandoResource(externalDatabase);
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status) {
        customResource.getStatus().putServerStatus(status);
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String namespace, String name) {
        Map<String, T> customResources = getNamespace(namespace).getCustomResources(clzz);
        return customResources.get(name);
    }

    @Override
    public void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase) {
        entandoCustomResource.getStatus().setEntandoDeploymentPhase(phase);
    }

    @Override
    public void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason) {
        entandoCustomResource.getStatus().findCurrentServerStatus()
                .orElseThrow(() -> new IllegalStateException("No server status recorded yet!"))
                .finishWith(new EntandoControllerFailureBuilder().withException(reason).build());
    }

    @Override
    public Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        NamespaceDouble namespace = getNamespace(resource);
        Optional<EntandoDatabaseService> first = namespace.getCustomResources(EntandoDatabaseService.class).values().stream()
                .filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms() == vendor).findFirst();
        return first.map(edb -> new ExternalDatabaseDeployment(
                namespace.getService(edb.getMetadata().getName() + "-" + KubeUtils.DEFAULT_SERVICE_SUFFIX),
                namespace.getEndpoints(edb.getMetadata().getName() + "-endpoints"), edb));
    }

    @Override
    public KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource) {
        return new KeycloakConnectionSecret(getNamespaces().get(CONTROLLER_NAMESPACE)
                .getSecret(resource.getKeycloakSecretToUse().orElse(EntandoOperatorConfig.getDefaultKeycloakSecretName())));
    }

    @Override
    public InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource) {
        return new InfrastructureConfig(getNamespaces().get(CONTROLLER_NAMESPACE)
                .getSecret(EntandoOperatorConfig.getEntandoInfrastructureSecretName()));
    }

    @Override
    public ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource) {
        NamespaceDouble namespace = getNamespace(resource);
        Service service = namespace.getService(
                resource.getMetadata().getName() + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-" + KubeUtils.DEFAULT_SERVICE_SUFFIX);
        Ingress ingress = namespace.getIngress(KubeUtils.standardIngressName(resource));
        return new ServiceDeploymentResult(service, ingress);
    }

}
