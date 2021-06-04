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

package org.entando.kubernetes.controller.support.creators;

import static org.entando.kubernetes.controller.support.creators.IngressCreator.getIngressServerUrl;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SsoAwareContainer;
import org.entando.kubernetes.controller.spi.container.SsoClientConfig;
import org.entando.kubernetes.controller.spi.container.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class KeycloakClientCreator {

    private final EntandoCustomResource entandoCustomResource;

    public KeycloakClientCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public boolean requiresKeycloakClients(Deployable<?> deployable) {
        return deployable instanceof PublicIngressingDeployable
                || deployable.getContainers().stream().anyMatch(SsoAwareContainer.class::isInstance);
    }

    public void createKeycloakClients(SecretClient secrets, SimpleKeycloakClient keycloak, Deployable<?> deployable,
            Optional<Ingress> ingress) {
        login(keycloak, deployable);
        if (deployable instanceof PublicIngressingDeployable) {
            //Create a public keycloak client
            PublicIngressingDeployable<?> publicIngressingDeployable = (PublicIngressingDeployable<?>) deployable;
            keycloak.createPublicClient(publicIngressingDeployable.getKeycloakRealmToUse(),
                    publicIngressingDeployable.getPublicClientIdToUse(),
                    getIngressServerUrl(ingress.orElseThrow(IllegalStateException::new)));

        }
        deployable.getContainers().stream()
                .filter(SsoAwareContainer.class::isInstance)
                .map(SsoAwareContainer.class::cast)
                .forEach(keycloakAware -> createClient(secrets, keycloak, keycloakAware, ingress));
    }

    private void login(SimpleKeycloakClient client, Deployable<?> deployable) {
        SsoConnectionInfo ssoConnectionInfo;
        if (deployable instanceof PublicIngressingDeployable) {
            ssoConnectionInfo = ((PublicIngressingDeployable<?>) deployable).getSsoConnectionInfo();
        } else {
            ssoConnectionInfo = deployable.getContainers().stream()
                    .filter(SsoAwareContainer.class::isInstance)
                    .map(SsoAwareContainer.class::cast)
                    .map(SsoAwareContainer::getSsoConnectionInfo)
                    .findAny()
                    .orElseThrow(IllegalArgumentException::new);
        }
        client.login(ssoConnectionInfo.getBaseUrlToUse(), ssoConnectionInfo.getUsername(),
                ssoConnectionInfo.getPassword());
    }

    private void createClient(SecretClient secrets, SimpleKeycloakClient client, SsoAwareContainer container,
            Optional<Ingress> ingress) {
        SsoClientConfig ssoClientConfig = container.getSsoClientConfig();
        if (ingress.isPresent()) {
            ssoClientConfig = ssoClientConfig
                    .withRedirectUri(getIngressServerUrl(ingress.get()) + container.getWebContextPath() + "/*");
            if (ingress.get().getSpec().getTls().size() == 1) {
                //Also support redirecting to http for http services that don't have knowledge that they are exposed as https
                ssoClientConfig = ssoClientConfig.withRedirectUri(
                        "http://" + ingress.get().getSpec().getRules().get(0).getHost() + container.getWebContextPath() + "/*");
            }
            ssoClientConfig = ssoClientConfig
                    .withWebOrigin(getIngressServerUrl(ingress.get()));
        }
        String keycloakClientSecret = client.prepareClientAndReturnSecret(ssoClientConfig);
        String secretName = KeycloakName.forTheClientSecret(ssoClientConfig);
        secrets.createSecretIfAbsent(entandoCustomResource, new SecretBuilder()
                .withNewMetadata()
                .withOwnerReferences(ResourceUtils.buildOwnerReference(entandoCustomResource))
                .withName(secretName)
                .endMetadata()
                .addToStringData(KeycloakName.CLIENT_ID_KEY, ssoClientConfig.getClientId())
                .addToStringData(KeycloakName.CLIENT_SECRET_KEY, keycloakClientSecret)
                .build());
    }

}
