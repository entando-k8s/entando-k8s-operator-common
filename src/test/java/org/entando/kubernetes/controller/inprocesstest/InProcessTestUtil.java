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

package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.test.support.VolumeMatchAssertions;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

/**
 * Mostly a source of test fixture factories. TODO: These need to refactored to be in-process friendly
 */
public interface InProcessTestUtil extends VolumeMatchAssertions, K8SStatusBasedAnswers, K8SResourceArgumentMatchers,
        StandardArgumentCaptors, InProcessTestData {

    default <T extends KeycloakAwareSpec> KeycloakConnectionSecret emulateKeycloakDeployment(SimpleK8SClient<?> client) {
        Secret secret = new SecretBuilder().withNewMetadata().withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .endMetadata()
                .addToStringData(KubeUtils.USERNAME_KEY, MY_KEYCLOAK_ADMIN_USERNAME)
                .addToStringData(KubeUtils.PASSSWORD_KEY, MY_KEYCLOAK_ADMIN_PASSWORD)
                .build();
        client.secrets().overwriteControllerSecret(secret);
        ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG)
                .endMetadata()
                .addToData(KubeUtils.URL_KEY, MY_KEYCLOAK_BASE_URL)
                .addToData(KubeUtils.INTERNAL_URL_KEY, MY_KEYCLOAK_BASE_URL)
                .build();
        client.secrets().overwriteControllerConfigMap(configMap);
        return new KeycloakConnectionSecret(secret, configMap);
    }

    default <T extends KeycloakAwareSpec> void emulateClusterInfrastuctureDeployment(SimpleK8SClient<?> client) {
        EntandoClusterInfrastructure dummyClusterInfrastructure = newEntandoClusterInfrastructure();
        ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(InfrastructureConfig.connectionConfigMapNameFor(dummyClusterInfrastructure))
                .endMetadata()
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY, "asdf")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY, "http://som.com/asdf")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY, "http://som.com/asdf")
                .build();
        client.secrets().createConfigMapIfAbsent(dummyClusterInfrastructure, configMap);
    }
}
