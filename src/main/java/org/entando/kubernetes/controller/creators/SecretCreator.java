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

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.HashMap;
import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.SecretToMount;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class SecretCreator extends AbstractK8SResourceCreator {

    public static final String DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME = "entando-default-ca-secret";
    public static final String CERT_SECRET_MOUNT_ROOT = "/etc/entando/certs";
    public static final SecretToMount DEFAULT_CERTIFICATE_AUTHORITY_SECRET_TO_MOUNT = new SecretToMount(
            DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, CERT_SECRET_MOUNT_ROOT + "/" + DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME);
    public static final String TRUSTSTORE_SETTINGS_KEY = "TRUSTSTORE_SETTINGS";
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String TRUST_STORE_PATH = standardCertPathOf(TRUST_STORE_FILE);

    public SecretCreator(EntandoBaseCustomResource<?> entandoCustomResource) {
        super(entandoCustomResource);
    }

    public static String standardCertPathOf(String filename) {
        return format("%s/%s/%s", CERT_SECRET_MOUNT_ROOT, DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, filename);
    }

    public void createSecrets(SecretClient client, Deployable<?,?> deployable) {
        if (TlsHelper.getInstance().isTrustStoreAvailable()) {
            client.createSecretIfAbsent(entandoCustomResource, newCertificateAuthoritySecret());
        }
        if (shouldCreateIngressTlsSecret(deployable)) {
            createIngressTlsSecret(client);
        }
        if (deployable instanceof Secretive) {
            for (Secret secret : ((Secretive) deployable).buildSecrets()) {
                createSecret(client, secret);
            }
        }
    }

    private void createIngressTlsSecret(SecretClient client) {
        Secret tlsSecret = new SecretBuilder()
                .withMetadata(fromCustomResource(true, entandoCustomResource.getMetadata().getName() + "-tls-secret"))
                .withType("kubernetes.io/tls")
                .withData(new HashMap<>())
                .build();
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            tlsSecret.getData().put(TlsHelper.TLS_CRT, TlsHelper.getInstance().getTlsCertBase64());
            tlsSecret.getData().put(TlsHelper.TLS_KEY, TlsHelper.getInstance().getTlsKeyBase64());
        } else {
            tlsSecret.getData().put(TlsHelper.TLS_CRT, "");
            tlsSecret.getData().put(TlsHelper.TLS_KEY, "");
        }
        createSecret(client, tlsSecret);
    }

    private boolean shouldCreateIngressTlsSecret(Deployable<?,?> deployable) {
        return deployable instanceof IngressingDeployable
                && (!((IngressingDeployable<?,?>) deployable).isTlsSecretSpecified())
                && TlsHelper.canAutoCreateTlsSecret();
    }

    @SuppressWarnings("squid:S2068")//Because it is not a hardcoded password
    private Secret newCertificateAuthoritySecret() {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withName(DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME)
                .endMetadata()
                .addToData(TRUST_STORE_FILE, TlsHelper.getInstance().getTrustStoreBase64())
                .addToStringData(
                        TRUSTSTORE_SETTINGS_KEY,
                        format("-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=%s", TRUST_STORE_PATH,
                                TlsHelper.getInstance().getTrustStorePassword()))
                .build();
        EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData().put(path.getFileName().toString(),
                TlsHelper.getInstance().getTlsCaCertBase64(path)));
        return secret;
    }

    private void createSecret(SecretClient client, Secret secret) {
        ObjectMeta metadata = fromCustomResource(true, secret.getMetadata().getName());
        Optional.ofNullable(secret.getMetadata().getLabels()).ifPresent(map -> metadata.getLabels().putAll(map));
        secret.setMetadata(metadata);
        client.createSecretIfAbsent(entandoCustomResource, secret);
    }
}
