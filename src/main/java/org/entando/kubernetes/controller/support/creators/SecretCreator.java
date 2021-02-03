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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.HashMap;
import org.entando.kubernetes.controller.spi.container.SecretToMount;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.EntandoCustomResource;

public class SecretCreator extends AbstractK8SResourceCreator {

    public static final String CERT_SECRET_MOUNT_ROOT = "/etc/entando/certs";
    public static final SecretToMount DEFAULT_CERTIFICATE_AUTHORITY_SECRET_TO_MOUNT = new SecretToMount(
            TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME,
            CERT_SECRET_MOUNT_ROOT + "/" + TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME);
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String TRUST_STORE_PATH = standardCertPathOf(TRUST_STORE_FILE);

    public SecretCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public static String standardCertPathOf(String filename) {
        return format("%s/%s/%s", CERT_SECRET_MOUNT_ROOT, TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, filename);
    }
    //TODO simplify the secret creation. In each namespace, we really only need:
    // 1. one secret for the TLS keypair
    // 2. one TLS secret with and empty keypair
    // 3. one secret for the Java truststore
    // 4. one secret for the CA certs
    // See ControllerExecutor for further complexity that can be eliminated
    // Proposed approach:
    // 1. Extract all CA and TLS info on Operator startup
    // 2. Create all secrets with predefined names in operator namespace
    // 3. ensure secrets are in deployment namespace
    // 4. Bind all deployments to the correct standard certs as required

    public void createSecrets(SecretClient client, Deployable<?> deployable) {
        if (TlsHelper.getInstance().isTrustStoreAvailable()) {
            client.createSecretIfAbsent(entandoCustomResource, newCertificateAuthoritySecret());
        }
        if (shouldCreateIngressTlsSecret(deployable)) {
            createIngressTlsSecret(client);
        }
        if (deployable instanceof Secretive) {
            for (Secret secret : ((Secretive) deployable).getSecrets()) {
                createSecret(client, secret);
            }
        }
        EntandoOperatorConfig.getImagePullSecrets().forEach(s -> ofNullable(client.loadControllerSecret(s))
                .ifPresent(secret -> client.createSecretIfAbsent(entandoCustomResource,
                        new SecretBuilder()
                                .withNewMetadata()
                                .withName(secret.getMetadata().getName())
                                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                                .withLabels(secret.getMetadata().getLabels())
                                .withAnnotations(secret.getMetadata().getAnnotations())
                                .endMetadata()
                                .withType(secret.getType())
                                .withData(secret.getData())
                                .withStringData(secret.getStringData())
                                .build())));
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

    private boolean shouldCreateIngressTlsSecret(Deployable<?> deployable) {
        return deployable instanceof IngressingDeployable
                && (((IngressingDeployable<?>) deployable).getTlsSecretName().isEmpty())
                && TlsHelper.canAutoCreateTlsSecret();
    }

    @SuppressWarnings("squid:S2068")//Because it is not a hardcoded password
    private Secret newCertificateAuthoritySecret() {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withName(TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME)
                .endMetadata()
                .addToData(TRUST_STORE_FILE, TlsHelper.getInstance().getTrustStoreBase64())
                .addToStringData(
                        TlsAware.TRUSTSTORE_SETTINGS_KEY,
                        format("-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=%s", TRUST_STORE_PATH,
                                TlsHelper.getInstance().getTrustStorePassword()))
                .build();
        EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData().put(path.getFileName().toString(),
                TlsHelper.getInstance().getTlsCaCertBase64(path)));
        return secret;
    }

    private void createSecret(SecretClient client, Secret secret) {
        ObjectMeta metadata = fromCustomResource(true, secret.getMetadata().getName());
        ofNullable(secret.getMetadata().getLabels()).ifPresent(map -> metadata.getLabels().putAll(map));
        secret.setMetadata(metadata);
        client.createSecretIfAbsent(entandoCustomResource, secret);
    }
}
