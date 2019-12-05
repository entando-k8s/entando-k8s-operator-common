package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Gettable;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext.Builder;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.creators.IngressCreator;

public final class IntegrationClientFactory {

    public static final String ENTANDO_CONTROLLERS_NAMESPACE = EntandoOperatorE2ETestConfig.calculateNameSpace("entando-controllers");

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

    static void recreateNamespaces(KubernetesClient client, String... nss) {
        Set<String> namespaces = new HashSet<>(Arrays.asList(nss));
        for (String namespace : namespaces) {
            if (client.namespaces().withName(namespace).get() != null) {
                deleteResourcesByDeployment(client, namespace);
                deleteResourcesByPod(client, namespace);
                deleteResourcesBySecret(client, namespace);
                deleteResourcesByPersistentVolumeClaim(client, namespace);
                deleteResourcesByService(client, namespace);
            } else {
                createNamespace(client, namespace);
            }
        }
    }

    private static void deleteResourcesByDeployment(KubernetesClient client, String namespace) {
        Lister<Deployment> lister = (c, ns) -> c.apps().deployments().inNamespace(ns).list();
        Getter<Deployment> getter = (c, ns, n) -> c.apps().deployments().inNamespace(ns).withName(n).fromServer();
        deleteOwnersOf(client, namespace, lister, getter);
    }

    private static void deleteResourcesByService(KubernetesClient client, String namespace) {
        Lister<Service> lister = (c, ns) -> c.services().inNamespace(ns).list();
        Getter<Service> getter = (c, ns, n) -> c.services().inNamespace(ns).withName(n).fromServer();
        deleteOwnersOf(client, namespace, lister, getter);
    }

    private static void deleteResourcesByPod(KubernetesClient client, String namespace) {
        Lister<Pod> lister = (c, ns) -> c.pods().inNamespace(ns).list();
        Getter<Pod> getter = (c, ns, n) -> c.pods().inNamespace(ns).withName(n).fromServer();
        deleteOwnersOf(client, namespace, lister, getter);
    }

    private static void deleteResourcesBySecret(KubernetesClient client, String namespace) {
        Lister<Secret> lister = (c, ns) -> c.secrets().inNamespace(ns).list();
        Getter<Secret> getter = (c, ns, n) -> c.secrets().inNamespace(ns).withName(n).fromServer();
        deleteOwnersOf(client, namespace, lister, getter);
    }

    private static void deleteResourcesByPersistentVolumeClaim(KubernetesClient client, String namespace) {
        Lister<PersistentVolumeClaim> lister = (c, ns) -> c.persistentVolumeClaims().inNamespace(ns).list();
        Getter<PersistentVolumeClaim> getter = (c, ns, n) -> c.persistentVolumeClaims().inNamespace(ns).withName(n).fromServer();
        deleteOwnersOf(client, namespace, lister, getter);
    }

    private static <T extends HasMetadata> void deleteOwnersOf(KubernetesClient client, String namespace, Lister<T> lister,
            Getter<T> getter) {
        Optional<T> childResource;
        do {
            childResource = lister.list(client, namespace).getItems().stream().filter(IntegrationClientFactory::isOwnedByEntandoResource)
                    .findFirst();
            childResource.ifPresent(d -> deleteOwnerAndWait(client, d, getter.get(client, namespace, d.getMetadata().getName())));

        } while (childResource.isPresent());
    }

    private static boolean isOwnedByEntandoResource(HasMetadata d) {
        return d.getMetadata().getOwnerReferences().size() == 1 && d.getMetadata().getOwnerReferences().get(0)
                .getApiVersion().startsWith("entando.org");
    }

    private static <T extends HasMetadata> void deleteOwnerAndWait(KubernetesClient client, T d, Gettable<T> gettable) {
        try {
            OwnerReference ownerReference = d.getMetadata().getOwnerReferences().get(0);
            RawCustomResourceOperationsImpl namespaced = client.customResource(new Builder()
                    .withName(ownerReference.getKind())
                    .withPlural(ownerReference.getKind().toLowerCase() + "s")
                    .withGroup("entando.org")
                    .withVersion(ownerReference.getApiVersion().substring("entando.org/".length()))
                    .withScope("Namespaced")
                    .build());
            try {
                Map<String, Object> customResource = namespaced.get(d.getMetadata().getNamespace(), ownerReference.getName());
                namespaced.delete(d.getMetadata().getNamespace(), ownerReference.getName());
                await().atMost(60, TimeUnit.SECONDS).ignoreExceptions().until(() -> gettable.get() == null);
            } catch (KubernetesClientException e) {
                if (e.getCode() == 404) {
                    //Still terminating the resource, but its parent has been deleted already
                    await().atMost(90, TimeUnit.SECONDS).ignoreExceptions().until(() -> gettable.get() == null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createNamespace(KubernetesClient client, String namespace) {
        client.namespaces().createNew().withNewMetadata().withName(namespace)
                .addToLabels("testType", "end-to-end")
                .endMetadata().done();
    }

    public interface Lister<T extends HasMetadata> {

        KubernetesResourceList<T> list(KubernetesClient client, String namespace);
    }

    public interface Getter<T extends HasMetadata> {

        Gettable<T> get(KubernetesClient client, String namespace, String name);
    }

}