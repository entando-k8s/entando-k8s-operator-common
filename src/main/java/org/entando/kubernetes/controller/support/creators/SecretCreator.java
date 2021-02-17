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
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.SecretToMount;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.TlsHelper;
import org.entando.kubernetes.model.EntandoCustomResource;

public class SecretCreator extends AbstractK8SResourceCreator {

    public static final SecretToMount DEFAULT_CERTIFICATE_AUTHORITY_SECRET_TO_MOUNT = new SecretToMount(
            TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME,
            SecretUtils.CERT_SECRET_MOUNT_ROOT + "/" + TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME);
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String TRUST_STORE_PATH = SecretUtils.standardCertPathOf(TRUST_STORE_FILE);

    public SecretCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    //TODO simplify the secret creation. In each namespace, we really only need:
    // 1. one secret for the provided TLS keypair or the empty keypair if autogeneration supported. none if done externally
    // 2. one secret for the Java truststore
    // 3. one secret for the CA certs
    // See ControllerExecutor for further complexity that can be eliminated
    // Proposed approach:
    // 1. Extract all CA certs, build the trust store and create a secret for it with a predictable name
    // 2. Now all possible secrets used will be in the operator namespace
    // 3. On deployment, ensure secrets are in deployment namespace
    // 4. Bind all deployments to the correct standard certs as required
    // 5. Use the TrustStore by programmatically resolving the secret from controller pods

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
