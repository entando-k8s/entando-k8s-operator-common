package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.impl.TlsHelper;

public final class IntegrationClientFactory {

    public static final String ENTANDO_CONTROLLERS_NAMESPACE = EntandoOperatorE2ETestConfig.getTestNamespaceOverride()
            .orElse("entando-controllers");

    private IntegrationClientFactory() {

    }

    static AutoAdaptableKubernetesClient newClient() {
        DefaultSimpleK8SClient.registerCustomKinds();
        AutoAdaptableKubernetesClient result = buildKubernetesClient();
        initializeTls(result);
        return result;
    }

    private static void initializeTls(AutoAdaptableKubernetesClient result) {
        String domainSuffix = IngressCreator.determineRoutingSuffix(result.getMasterUrl().getHost());
        Path certRoot = Paths.get(EntandoOperatorE2ETestConfig.getTestsCertRoot());
        Path tlsPath = certRoot.resolve(domainSuffix);
        Path caCert = tlsPath.resolve("ca.crt");
        if (caCert.toFile().exists()) {
            System.setProperty(EntandoOperatorConfig.ENTANDO_CA_CERT_PATHS, caCert.toAbsolutePath().toString());
        }
        if (tlsPath.resolve("tls.crt").toFile().exists() && tlsPath.resolve("tls.key").toFile().exists()) {
            System.setProperty(EntandoOperatorConfig.ENTANDO_PATH_TO_TLS_KEYPAIR, tlsPath.toAbsolutePath().toString());
        }
        TlsHelper.getInstance().init();
    }

    private static AutoAdaptableKubernetesClient buildKubernetesClient() {
        ConfigBuilder configBuilder = new ConfigBuilder().withTrustCerts(true).withConnectionTimeout(30000).withRequestTimeout(30000);
        EntandoOperatorE2ETestConfig.getKubernetesMasterUrl().ifPresent(s -> configBuilder.withMasterUrl(s));
        EntandoOperatorE2ETestConfig.getKubernetesUsername().ifPresent(s -> configBuilder.withUsername(s));
        EntandoOperatorE2ETestConfig.getKubernetesPassword().ifPresent(s -> configBuilder.withPassword(s));
        Config config = configBuilder.build();
        OkHttpClient httpClient = HttpClientUtils.createHttpClient(config);
        AutoAdaptableKubernetesClient result = new AutoAdaptableKubernetesClient(httpClient, config);
        if (result.namespaces().withName(ENTANDO_CONTROLLERS_NAMESPACE).get() == null) {
            result.namespaces().createNew().withNewMetadata().withName(ENTANDO_CONTROLLERS_NAMESPACE).addToLabels("testType", "end-to-end")
                    .endMetadata().done();
        }
        //Has to be in entando-controllers
        if (!ENTANDO_CONTROLLERS_NAMESPACE.equals(result.getNamespace())) {
            result.close();
            config.setNamespace(ENTANDO_CONTROLLERS_NAMESPACE);
            result = new AutoAdaptableKubernetesClient(HttpClientUtils.createHttpClient(config), config);
        }
        System.setProperty(EntandoOperatorConfig.ENTANDO_OPERATOR_NAMESPACE_OVERRIDE, ENTANDO_CONTROLLERS_NAMESPACE);
        return result;
    }

    static void recreateNamespaces(KubernetesClient client, String... namespaces) {
        for (String namespace : namespaces) {
            if (client.namespaces().withName(namespace).get() != null) {
                client.namespaces().withName(namespace).delete();
            }
            await().atMost(240, TimeUnit.SECONDS).ignoreExceptions().until(
                    () -> client.namespaces().withName(namespace).get() == null);
        }
        for (String namespace : namespaces) {
            await().atMost(60, TimeUnit.SECONDS).ignoreExceptions().until(
                    () -> client.namespaces().createNew().withNewMetadata().withName(namespace).addToLabels("testType", "end-to-end")
                            .endMetadata().done()
                            != null);
        }
    }
}
