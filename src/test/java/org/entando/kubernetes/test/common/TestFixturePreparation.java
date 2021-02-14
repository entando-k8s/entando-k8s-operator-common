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

package org.entando.kubernetes.test.common;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.creators.IngressCreator;
import org.entando.kubernetes.controller.support.creators.TlsHelper;

public final class TestFixturePreparation {

    public static final String ENTANDO_CONTROLLERS_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("entando-controllers");
    public static final String CURRENT_ENTANDO_RESOURCE_VERSION = "v1";

    private TestFixturePreparation() {

    }

    public static AutoAdaptableKubernetesClient newClient() {
        AutoAdaptableKubernetesClient result = buildKubernetesClient();
        initializeTls(result);
        return result;
    }

    private static void initializeTls(AutoAdaptableKubernetesClient result) {
        String domainSuffix = IngressCreator.determineRoutingSuffix(result.getMasterUrl().getHost());
        Path certRoot = Paths.get(EntandoOperatorTestConfig.getTestsCertRoot());
        Path tlsPath = certRoot.resolve(domainSuffix);
        Path caCert = tlsPath.resolve("ca.crt");
        if (caCert.toFile().exists()) {
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.getJvmSystemProperty(),
                    caCert.toAbsolutePath().toString());
        }
        if (tlsPath.resolve("tls.crt").toFile().exists() && tlsPath.resolve("tls.key").toFile().exists()) {
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.getJvmSystemProperty(),
                    tlsPath.toAbsolutePath().toString());
        }
        TlsHelper.getInstance().init();
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT.getJvmSystemProperty(), "true");
    }

    private static AutoAdaptableKubernetesClient buildKubernetesClient() {
        ConfigBuilder configBuilder = new ConfigBuilder().withTrustCerts(true).withConnectionTimeout(30000).withRequestTimeout(30000);
        EntandoOperatorTestConfig.getKubernetesMasterUrl().ifPresent(s -> configBuilder.withMasterUrl(s));
        EntandoOperatorTestConfig.getKubernetesUsername().ifPresent(s -> configBuilder.withUsername(s));
        EntandoOperatorTestConfig.getKubernetesPassword().ifPresent(s -> configBuilder.withPassword(s));
        Config config = configBuilder.build();
        OkHttpClient httpClient = HttpClientUtils.createHttpClient(config);
        AutoAdaptableKubernetesClient result = new AutoAdaptableKubernetesClient(httpClient, config);
        if (result.namespaces().withName(ENTANDO_CONTROLLERS_NAMESPACE).get() == null) {
            createNamespace(result, ENTANDO_CONTROLLERS_NAMESPACE);
        }
        //Has to be in entando-controllers
        if (!ENTANDO_CONTROLLERS_NAMESPACE.equals(result.getNamespace())) {
            result.close();
            config.setNamespace(ENTANDO_CONTROLLERS_NAMESPACE);
            result = new AutoAdaptableKubernetesClient(HttpClientUtils.createHttpClient(config), config);
        }
        ensureRedHatRegistryCredentials(result);
        return result;
    }

    private static void ensureRedHatRegistryCredentials(AutoAdaptableKubernetesClient result) {
        if (result.secrets().inNamespace(ENTANDO_CONTROLLERS_NAMESPACE).withName("redhat-registry").get() == null) {
            EntandoOperatorTestConfig.getRedhatRegistryCredentials().ifPresent(s -> {
                Secret cm = new SecretBuilder().withNewMetadata()
                        .withNamespace(ENTANDO_CONTROLLERS_NAMESPACE)
                        .withName("redhat-registry")
                        .endMetadata()
                        .addToStringData(".dockerconfigjson", s)
                        .withType("kubernetes.io/dockerconfigjson")
                        .build();
                result.secrets().inNamespace(ENTANDO_CONTROLLERS_NAMESPACE).create(cm);
            });
        }
    }

    public static void createNamespace(KubernetesClient client, String namespace) {
        Namespace n = new NamespaceBuilder()
                .withNewMetadata().withName(namespace)
                .addToLabels("testType", "end-to-end")
                .endMetadata().build();
        client.namespaces().create(n);

    }
}