package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.model.EntandoCustomResource;

public class SecretCreator extends AbstractK8SResourceCreator {

    public static final String DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME = "entando-default-ca-secret";

    public SecretCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public void createSecrets(SecretClient client, Deployable<?> deployable) {
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
                && (!((IngressingDeployable<?>) deployable).isTlsSecretSpecified())
                && TlsHelper.canAutoCreateTlsSecret();
    }

    private Secret newCertificateAuthoritySecret() {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withName(DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME)
                .endMetadata()
                .addToData(DeploymentCreator.TRUST_STORE_FILE, TlsHelper.getInstance().getTrustStoreBase64())
                .build();
        EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData().put(path.getFileName().toString(),
                TlsHelper.getInstance().getTlsCaCertBase64(path)));
        return secret;
    }

    private void createSecret(SecretClient client, Secret secret) {
        ObjectMeta metadata = fromCustomResource(true, secret.getMetadata().getName());
        metadata.getLabels().putAll(secret.getMetadata().getLabels());
        secret.setMetadata(metadata);
        client.createSecretIfAbsent(entandoCustomResource, secret);
    }
}
