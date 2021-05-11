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

package org.entando.kubernetes.controller.support.client.impl;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.client.impl.DefaultKubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.support.client.ConfigMapBasedKeycloakConnectionConfig;
import org.entando.kubernetes.controller.support.client.DoneableConfigMap;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class DefaultEntandoResourceClient extends DefaultKubernetesClientForControllers implements
        EntandoResourceClient, PatchableClient {

    public DefaultEntandoResourceClient(KubernetesClient client) {
        super(client);
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public ConfigMapBasedKeycloakConnectionConfig findKeycloak(EntandoCustomResource resource, KeycloakPreference keycloakPreference) {
        Optional<ResourceReference> keycloakToUse = determineKeycloakToUse(resource, keycloakPreference);
        String secretName = keycloakToUse.map(KeycloakName::forTheAdminSecret)
                .orElse(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET);
        String configMapName = keycloakToUse.map(KeycloakName::forTheConnectionConfigMap)
                .orElse(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG);
        String configMapNamespace = keycloakToUse
                .map(resourceReference -> resourceReference.getNamespace().orElseThrow(IllegalStateException::new))
                .orElse(client.getNamespace());
        //This secret is duplicated in the deployment namespace, but the controller can only read the one in its own namespace
        Secret secret = this.client.secrets().withName(secretName).fromServer().get();
        if (secret == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak secret %s in namespace %s", secretName, client.getNamespace()));
        }
        //The configmap comes from the deployment namespace, unless it is a pre-configured keycloak
        ConfigMap configMap = this.client.configMaps().inNamespace(configMapNamespace).withName(configMapName).fromServer().get();
        if (configMap == null) {
            throw new IllegalStateException(
                    format("Could not find the Keycloak ConfigMap %s in namespace %s", configMapName, configMapNamespace));
        }
        return new ConfigMapBasedKeycloakConnectionConfig(secret, configMap);

    }

    @Override
    public Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoCustomResource peerInNamespace) {
        List<EntandoKeycloakServer> items = getOperations(EntandoKeycloakServer.class)
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).list().getItems();
        if (items.size() == 1) {
            return Optional.of(items.get(0));
        }
        return Optional.empty();
    }

    @Override
    public DoneableConfigMap loadDefaultCapabilitiesConfigMap() {
        Resource<ConfigMap> resource = client.configMaps()
                .inNamespace(client.getNamespace())
                .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME);
        final ConfigMap configMap = resource.get();
        if (configMap == null) {
            return new DoneableConfigMap(client.configMaps()::create)
                    .withNewMetadata()
                    .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME)
                    .withNamespace(client.getNamespace())
                    .endMetadata()
                    .addToData(new HashMap<>());

        } else {
            return new DoneableConfigMap(configMap, client.configMaps().inNamespace(client.getNamespace())
                    .withName(KubeUtils.ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME)::patch)
                    .editMetadata()
                    .addToAnnotations(KubeUtils.UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
                    .endMetadata();
        }
    }

    @Override
    public ConfigMap loadDockerImageInfoConfigMap() {
        return client.configMaps().inNamespace(EntandoOperatorConfig.getEntandoDockerImageInfoNamespace().orElse(client.getNamespace()))
                .withName(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()).fromServer().get();
    }

    @Override
    public ExposedService loadExposedService(EntandoCustomResource resource) {
        return new ExposedService(
                loadService(resource, NameUtils.standardServiceName(resource)),
                loadIngress(resource, NameUtils.standardIngressName(resource)));
    }

    @Override
    public Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor) {
        List<EntandoDatabaseService> externalDatabaseList = getOperations(EntandoDatabaseService.class)
                .inNamespace(resource.getMetadata().getNamespace()).list().getItems();
        return externalDatabaseList.stream().filter(entandoDatabaseService -> entandoDatabaseService.getSpec().getDbms() == vendor)
                .findFirst().map(externalDatabase ->
                        new ExternalDatabaseDeployment(
                                loadService(externalDatabase, ExternalDatabaseDeployment.serviceName(externalDatabase)),
                                externalDatabase));
    }

    private Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    private Ingress loadIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    protected Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

}
