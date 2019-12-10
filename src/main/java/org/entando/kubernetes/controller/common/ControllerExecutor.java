package org.entando.kubernetes.controller.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public class ControllerExecutor {

    public static final String ETC_ENTANDO_TLS = "/etc/entando/tls";
    public static final String ETC_ENTANDO_CA = "/etc/entando/ca";
    private static final Map<String, String> resourceKindToImageNames = buildImageMap();
    private final DefaultSimpleK8SClient client;
    private String controllerNamespace;

    public ControllerExecutor(String controllerNamespace, KubernetesClient client) {
        this.controllerNamespace = controllerNamespace;
        this.client = new DefaultSimpleK8SClient(client);
    }

    private static Map<String, String> buildImageMap() {
        Map<String, String> map = new ConcurrentHashMap<>();
        map.put("EntandoKeycloakServer", "entando-k8s-keycloak-controller");
        map.put("EntandoClusterInfrastructure", "entando-k8s-cluster-infrastructure-controller");
        map.put("EntandoPlugin", "entando-k8s-plugin-controller");
        map.put("EntandoApp", "entando-k8s-app-controller");
        map.put("EntandoAppPluginLink", "entando-k8s-app-plugin-link-controller");
        return map;
    }

    public static Optional<String> resolveLatestImageFor(KubernetesClient client, Class<? extends EntandoBaseCustomResource> type) {
        String kind = KubeUtils.getKindOf(type);
        String imageName = resourceKindToImageNames.get(kind);
        ConfigMap configMap = client.configMaps().inNamespace(EntandoOperatorConfig.getEntandoImageNamespace())
                .withName(EntandoOperatorConfig.getEntandoK8sImageVersionsConfigmap()).get();
        if (configMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(configMap.getData().get(imageName));
    }

    public void startControllerFor(Action action, EntandoCustomResource resource, String imageVersionToUse) {
        Pod pod = new PodBuilder().withNewMetadata()
                .withName(resource.getMetadata().getName() + "-deployer-" + RandomStringUtils.randomAlphanumeric(10).toLowerCase())
                .withNamespace(this.controllerNamespace)
                .addToLabels(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resource.getKind())
                .addToLabels(resource.getKind(), resource.getMetadata().getName())
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("deployer")
                .withImage(determineControllerImage(resource, imageVersionToUse))
                .withImagePullPolicy("Always")
                .withEnv(buildEnvVars(action, resource))
                .withVolumeMounts(maybeCreateTlsVolumeMounts())
                .endContainer()
                .withVolumes(maybeCreateTlsVolumes(resource))
                .endSpec()
                .build();
        client.pods().start(pod);
    }

    private String determineControllerImage(EntandoCustomResource resource, String imageVersionToUse) {
        return EntandoOperatorConfig.getEntandoDockerRegistry() + "/" + EntandoOperatorConfig.getEntandoImageNamespace() + "/"
                + resourceKindToImageNames.get(resource.getKind()) + ":" + imageVersionToUse;
    }

    private List<EnvVar> buildEnvVars(Action action, EntandoCustomResource resource) {
        ArrayList<EnvVar> result = new ArrayList<>();
        result.add(new EnvVar("ENTANDO_RESOURCE_ACTION", action.name(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAMESPACE", resource.getMetadata().getNamespace(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAME", resource.getMetadata().getName(), null));
        result.add(new EnvVar("ENTANDO_K8S_OPERATOR_REGISTRY", EntandoOperatorConfig.getEntandoDockerRegistry(), null));
        result.add(new EnvVar("ENTANDO_USE_AUTO_CERT_GENERATION",
                String.valueOf(EntandoOperatorConfig.useAutoCertGeneration()), null));
        result.add(new EnvVar("ENTANDO_K8S_IMAGE_VERSION", EntandoOperatorConfig.getEntandoImageVersion(), null));
        result.add(new EnvVar("ENTANDO_K8S_OPERATOR_IMAGE_NAMESPACE", EntandoOperatorConfig.getEntandoImageNamespace(),
                null));
        result.add(
                new EnvVar("ENTANDO_K8S_OPERATOR_SECURITY_MODE", EntandoOperatorConfig.getOperatorSecurityMode().name(),
                        null));
        result.add(new EnvVar("ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS",
                String.valueOf(EntandoOperatorConfig.getPodCompletionTimeoutSeconds()), null));
        result.add(new EnvVar("ENTANDO_POD_READINESS_TIMEOUT_SECONDS",
                String.valueOf(EntandoOperatorConfig.getPodReadinessTimeoutSeconds()), null));
        result.add(new EnvVar("ENTANDO_K8S_OPERATOR_REGISTRY", EntandoOperatorConfig.getEntandoDockerRegistry(), null));
        EntandoOperatorConfig.getOperatorNamespaceOverride()
                .ifPresent(s -> result.add(new EnvVar("ENTANDO_OPERATOR_NAMESPACE_OVERRIDE", s, null)));
        EntandoOperatorConfig.getDefaultRoutingSuffix()
                .ifPresent(s -> result.add(new EnvVar("ENTANDO_DEFAULT_ROUTING_SUFFIX", s, null)));
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            StringBuilder sb = new StringBuilder();
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path ->
                    sb.append("/etc/entando/ca/").append(path.getFileName().toString()).append(" "));
            result.add(new EnvVar("ENTANDO_CA_CERT_PATHS", sb.toString().trim(), null));
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new EnvVar("ENTANDO_PATH_TO_TLS_KEYPAIR", ETC_ENTANDO_TLS, null));
        }
        return result;
    }

    private List<Volume> maybeCreateTlsVolumes(EntandoCustomResource resource) {
        List<Volume> result = new ArrayList<>();
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            Secret secret = new SecretBuilder().withNewMetadata().withName(resource.getMetadata().getName() + "-ca-cert-secret")
                    .endMetadata().build();
            //Add all available CA Certs. No need to map the trustStore itself - the controller will build this up internally
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData()
                    .put(path.getFileName().toString(), TlsHelper.getInstance().getTlsCaCertBase64(path)));
            client.secrets().overwriteControllerSecret(secret);
            result.add(new VolumeBuilder().withName("ca-cert-volume").withNewSecret()
                    .withSecretName(resource.getMetadata().getName() + "-ca-cert-secret").endSecret()
                    .build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            Secret secret = new SecretBuilder().withNewMetadata().withName(resource.getMetadata().getName() + "-tls-secret")
                    .endMetadata()
                    .addToData(TlsHelper.TLS_KEY, TlsHelper.getInstance().getTlsKeyBase64())
                    .addToData(TlsHelper.TLS_CRT, TlsHelper.getInstance().getTlsCertBase64())
                    .build();
            client.secrets().overwriteControllerSecret(secret);
            result.add(new VolumeBuilder().withName("tls-volume").withNewSecret()
                    .withSecretName(resource.getMetadata().getName() + "-tls-secret").endSecret()
                    .build());
        }
        return result;
    }

    private List<VolumeMount> maybeCreateTlsVolumeMounts() {
        List<VolumeMount> result = new ArrayList<>();
        if (EntandoOperatorConfig.getCertificateAuthorityCertPaths().size() > 0) {
            result.add(new VolumeMountBuilder().withName("ca-cert-volume").withMountPath(ETC_ENTANDO_CA).build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new VolumeMountBuilder().withName("tls-volume").withMountPath(ETC_ENTANDO_TLS).build());
        }
        return result;
    }

}
