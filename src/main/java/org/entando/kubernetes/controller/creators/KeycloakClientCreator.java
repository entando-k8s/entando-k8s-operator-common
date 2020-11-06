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

package org.entando.kubernetes.controller.creators;

import static org.entando.kubernetes.controller.creators.IngressCreator.getIngressServerUrl;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.KeycloakAware;
import org.entando.kubernetes.controller.spi.PublicIngressingDeployable;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.KeycloakAwareSpec;

public class KeycloakClientCreator {

    private final EntandoBaseCustomResource<?> entandoCustomResource;

    public KeycloakClientCreator(EntandoBaseCustomResource<?> entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public boolean requiresKeycloakClients(Deployable<?, ?> deployable) {
        return deployable instanceof PublicIngressingDeployable
                || deployable.getContainers().stream().anyMatch(KeycloakAware.class::isInstance);
    }

    public void createKeycloakClients(SecretClient secrets, SimpleKeycloakClient keycloak, Deployable<?, ?> deployable,
            Optional<Ingress> ingress) {
        login(keycloak, deployable);
        if (deployable instanceof PublicIngressingDeployable && this.entandoCustomResource.getSpec() instanceof KeycloakAwareSpec) {
            //Create a public keycloak client
            KeycloakAwareSpec spec = (KeycloakAwareSpec) this.entandoCustomResource.getSpec();
            PublicIngressingDeployable<?, ?> publicIngressingDeployable = (PublicIngressingDeployable<?, ?>) deployable;
            keycloak.createPublicClient(KeycloakName.ofTheRealm(spec),
                    KeycloakName.ofThePublicClient(publicIngressingDeployable.getCustomResource().getSpec()),
                    getIngressServerUrl(ingress.orElseThrow(IllegalStateException::new)));

        }
        deployable.getContainers().stream()
                .filter(KeycloakAware.class::isInstance)
                .map(KeycloakAware.class::cast)
                .forEach(keycloakAware -> createClient(secrets, keycloak, keycloakAware, ingress));
    }

    private void login(SimpleKeycloakClient client, Deployable<?, ?> deployable) {
        KeycloakConnectionConfig keycloakConnectionConfig;
        if (deployable instanceof PublicIngressingDeployable) {
            keycloakConnectionConfig = ((PublicIngressingDeployable) deployable).getKeycloakDeploymentResult();
        } else {
            keycloakConnectionConfig = deployable.getContainers().stream()
                    .filter(KeycloakAware.class::isInstance)
                    .map(KeycloakAware.class::cast)
                    .map(KeycloakAware::getKeycloakConnectionConfig)
                    .findAny()
                    .orElseThrow(IllegalArgumentException::new);
        }
        client.login(keycloakConnectionConfig.determineBaseUrl(), keycloakConnectionConfig.getUsername(),
                keycloakConnectionConfig.getPassword());
    }

    private void createClient(SecretClient secrets, SimpleKeycloakClient client, KeycloakAware container, Optional<Ingress> ingress) {
        KeycloakClientConfig keycloakConfig = container.getKeycloakClientConfig();
        KeycloakClientConfig keycloakClientConfig = container.getKeycloakClientConfig();
        if (ingress.isPresent()) {
            keycloakClientConfig = keycloakClientConfig
                    .withRedirectUri(getIngressServerUrl(ingress.get()) + container.getWebContextPath() + "/*");
            if (ingress.get().getSpec().getTls().size() == 1) {
                //Also support redirecting to http for http services that don't have knowledge that they are exposed as https
                keycloakClientConfig = keycloakClientConfig.withRedirectUri(
                        "http://" + ingress.get().getSpec().getRules().get(0).getHost() + container.getWebContextPath() + "/*");
            }
            keycloakClientConfig = keycloakClientConfig
                    .withWebOrigin(getIngressServerUrl(ingress.get()));
        }
        String keycloakClientSecret = client.prepareClientAndReturnSecret(keycloakClientConfig);
        String secretName = KeycloakName.forTheClientSecret(keycloakConfig);
        secrets.createSecretIfAbsent(entandoCustomResource, new SecretBuilder()
                .withNewMetadata()
                .withOwnerReferences(KubeUtils.buildOwnerReference(entandoCustomResource))
                .withName(secretName)
                .endMetadata()
                .addToStringData(KeycloakName.CLIENT_ID_KEY, keycloakConfig.getClientId())
                .addToStringData(KeycloakName.CLIENT_SECRET_KEY, keycloakClientSecret)
                .build());
    }

}
