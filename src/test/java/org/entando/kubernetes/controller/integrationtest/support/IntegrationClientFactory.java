package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext.Builder;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public final class IntegrationClientFactory {

    public static final String ENTANDO_CONTROLLERS_NAMESPACE = EntandoOperatorE2ETestConfig.calculateNameSpace("entando-controllers");
    public static final String CURRENT_ENTANDO_RESOURCE_VERSION = "v1alpha1";

    private IntegrationClientFactory() {

    }

    public static AutoAdaptableKubernetesClient newClient() {
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

    public static void setTextFixture(KubernetesClient client, TestFixtureRequest testFixtureRequest) {
        for (Entry<String, List<Class<? extends EntandoBaseCustomResource>>> entry : testFixtureRequest.getRequiredDeletions().entrySet()) {
            if (client.namespaces().withName(entry.getKey()).get() != null) {
                for (Class<? extends EntandoBaseCustomResource> type : entry.getValue()) {
                    deleteFromNamespace(client, type, entry.getKey());
                }
            } else {
                createNamespace(client, entry.getKey());
            }
        }
    }

    private static void deleteFromNamespace(KubernetesClient client, Class<? extends EntandoBaseCustomResource> type, String namespace) {
        CustomResourceDefinitionContext context = new Builder()
                .withScope("Namespaced")
                .withGroup("entando.org")
                .withVersion(CURRENT_ENTANDO_RESOURCE_VERSION)
                .withPlural(getPluralFrom(type))
                .build();
        client.customResource(context).delete(namespace);
        await().ignoreExceptions().atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    List items = (List) client.customResource(context).list(namespace).get("items");
                    return items.size() == 0;
                });
    }

    protected static String getPluralFrom(Class<? extends EntandoBaseCustomResource> type) {
        try {
            return type.getConstructor().newInstance().getDefinitionName().split("\\.")[0];
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void createNamespace(KubernetesClient client, String namespace) {
        client.namespaces().createNew().withNewMetadata().withName(namespace)
                .addToLabels("testType", "end-to-end")
                .endMetadata().done();
    }
}