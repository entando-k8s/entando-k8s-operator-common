package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.integrationtest.support.DeletionWaiter.delete;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public final class TestFixturePreparation {

    public static final String ENTANDO_CONTROLLERS_NAMESPACE = EntandoOperatorE2ETestConfig.calculateNameSpace("entando-controllers");
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
        System.setProperty(EntandoOperatorConfig.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT,
                String.valueOf(TlsHelper.getDefaultProtocol().equals("http")));
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
            createNamespace(result, ENTANDO_CONTROLLERS_NAMESPACE);
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

    @SuppressWarnings("unchecked")
    public static void prepareTestFixture(KubernetesClient client, TestFixtureRequest testFixtureRequest) {
        for (Entry<String, List<Class<? extends EntandoBaseCustomResource>>> entry : testFixtureRequest.getRequiredDeletions().entrySet()) {
            if (client.namespaces().withName(entry.getKey()).get() != null) {
                for (Class<? extends EntandoBaseCustomResource> type : entry.getValue()) {
                    //This is a bit heavy-handed, but we need  to make absolutely sure the pods are deleted before the test starts
                    //Pods are considered 'deleted' even if they are still gracefully shutting down and the second or two
                    // it takes to shut down can interfere with subsequent pod watchers.
                    delete(client.apps().deployments()).fromNamespace(entry.getKey())
                            .withLabel(KubeUtils.getKindOf(type))
                            .waitingAtMost(60, TimeUnit.SECONDS);
                    delete(client.pods()).fromNamespace(entry.getKey())
                            .withLabel(KubeUtils.getKindOf(type))
                            .waitingAtMost(60, TimeUnit.SECONDS);
                    new CustomResourceDeletionWaiter(client, KubeUtils.getKindOf(type)).fromNamespace(entry.getKey())
                            .waitingAtMost(120, TimeUnit.SECONDS);
                }
            } else {
                createNamespace(client, entry.getKey());
            }
        }
    }

    private static void createNamespace(KubernetesClient client, String namespace) {
        client.namespaces().createNew().withNewMetadata().withName(namespace)
                .addToLabels("testType", "end-to-end")
                .endMetadata().done();
    }
}