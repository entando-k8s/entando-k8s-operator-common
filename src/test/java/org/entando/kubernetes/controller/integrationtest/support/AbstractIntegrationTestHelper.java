package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.cdi.DefaultIngressClient;
import org.entando.kubernetes.cdi.Producers;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.impl.TlsHelper;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;

public class AbstractIntegrationTestHelper {

    public static final String[] TEST_NAMESPACES = {"keycloak-namespace", "test-namespace", "plugin-namespace", "entando-infra-namespace"};
    public static final String K8S = "k8s";
    public static final String INTEGRATION_TARGET_ENVIRONMENT = "entando.k8s.operator.tests.run.target";
    public static final String TESTS_CERT_ROOT = "entando.k8s.operator.tests.cert.root";
    public static final String ENTANDO_CONTROLLERS = "entando-controllers";
    protected final DefaultKubernetesClient client;
    private final String domainSuffix;

    protected AbstractIntegrationTestHelper() {
        this(newClient());
    }

    protected AbstractIntegrationTestHelper(DefaultKubernetesClient client) {
        this.client = client;
        domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
    }

    protected static void logWarning(String x) {
        System.out.println(x);
    }

    private static AutoAdaptableKubernetesClient newClient() {
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
        if (result.namespaces().withName(ENTANDO_CONTROLLERS).get() == null) {
            result.namespaces().createNew().withNewMetadata().withName(ENTANDO_CONTROLLERS).addToLabels("testType", "end-to-end")
                    .endMetadata().done();
        }
        //Has to be in entando-controllers
        if (!ENTANDO_CONTROLLERS.equals(result.getNamespace())) {
            try {
                Producers.destroyOkHttpClient(httpClient);
            } catch (IOException e) {
                logWarning(e.toString());
            }
            result.close();
            config.setNamespace(ENTANDO_CONTROLLERS);
            result = new AutoAdaptableKubernetesClient(HttpClientUtils.createHttpClient(config), config);
        }
        System.setProperty(EntandoOperatorConfig.ENTANDO_OPERATOR_NAMESPACE_OVERRIDE, ENTANDO_CONTROLLERS);
        return result;
    }

    public void recreateNamespaces(String... namespaces) {
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

    public String getDomainSuffix() {
        return domainSuffix;
    }

    public JobPodWaiter waitForJobPod(JobPodWaiter mutex, String namespace, String jobName) {
        waitFor(20).seconds().orUntil(
                () -> client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems()
                        .size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    public ServicePodWaiter waitForServicePod(ServicePodWaiter mutex, String namespace, String deploymentName) {
        waitFor(20).seconds().orUntil(
                () -> client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                        .getItems().size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                .getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }
}
