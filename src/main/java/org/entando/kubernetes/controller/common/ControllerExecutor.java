package org.entando.kubernetes.controller.common;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoImageResolver;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ControllerExecutor {

    public static final String ETC_ENTANDO_TLS = "/etc/entando/tls";
    public static final String ETC_ENTANDO_CA = "/etc/entando/ca";
    private static final Map<String, String> resourceKindToImageNames = buildImageMap();
    private final SimpleK8SClient<?> client;
    private final EntandoImageResolver imageResolver;
    private String controllerNamespace;

    public ControllerExecutor(String controllerNamespace, KubernetesClient client) {
        this(controllerNamespace, new DefaultSimpleK8SClient(client));
    }

    public ControllerExecutor(String controllerNamespace, SimpleK8SClient<?> client) {
        this.controllerNamespace = controllerNamespace;
        this.client = client;
        this.imageResolver = new EntandoImageResolver(
                client.secrets().loadControllerConfigMap(EntandoOperatorConfig.getEntandoDockerImageVersionsConfigMap()));
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

    public static String resolveControllerImageName(EntandoCustomResource resource) {
        return resolveControllerImageNameByKind(resource.getKind());
    }

    public static String resolveControllerImageName(Class<? extends EntandoBaseCustomResource> type) {
        String kind = KubeUtils.getKindOf(type);
        return resolveControllerImageNameByKind(kind);
    }

    private static String resolveControllerImageNameByKind(String kind) {
        return resourceKindToImageNames.get(kind);
    }

    public Optional<String> resolveLatestImageFor(Class<? extends EntandoBaseCustomResource> type) {
        String imageName = resolveControllerImageName(type);
        return this.imageResolver.determineLatestVersionOf(imageName);
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
        return this.imageResolver.determineImageUri(
                "entando/" + resolveControllerImageName(resource), Optional.ofNullable(imageVersionToUse));
    }

    private List<EnvVar> buildEnvVars(Action action, EntandoCustomResource resource) {
        ArrayList<EnvVar> result = new ArrayList<>();
        result.add(new EnvVar("ENTANDO_RESOURCE_ACTION", action.name(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAMESPACE", resource.getMetadata().getNamespace(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAME", resource.getMetadata().getName(), null));
        result.addAll(Stream.of(EntandoOperatorConfigProperty.values())
                .filter(prop -> EntandoOperatorConfigBase.lookupProperty(prop).isPresent())
                .map(prop -> new EnvVar(prop.name(), EntandoOperatorConfigBase.lookupProperty(prop).get(), null))
                .collect(Collectors.toList()));
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path ->
                    sb.append("/etc/entando/ca/").append(path.getFileName().toString()).append(" "));
            result.add(new EnvVar(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.name(), sb.toString().trim(), null));
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new EnvVar(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.name(), ETC_ENTANDO_TLS, null));
        }
        return result;
    }

    private List<Volume> maybeCreateTlsVolumes(EntandoCustomResource resource) {
        List<Volume> result = new ArrayList<>();
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            Secret secret = new SecretBuilder().withNewMetadata().withName(resource.getMetadata().getName() + "-controller-ca-cert-secret")
                    .endMetadata().build();
            //Add all available CA Certs. No need to map the trustStore itself - the controller will build this up internally
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData()
                    .put(path.getFileName().toString(), TlsHelper.getInstance().getTlsCaCertBase64(path)));
            client.secrets().overwriteControllerSecret(secret);
            result.add(new VolumeBuilder().withName("ca-cert-volume").withNewSecret()
                    .withSecretName(resource.getMetadata().getName() + "-controller-ca-cert-secret").endSecret()
                    .build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            Secret secret = new SecretBuilder().withNewMetadata().withName(resource.getMetadata().getName() + "-controller-tls-secret")
                    .endMetadata()
                    .withType("kubernetes.io/tls")
                    .addToData(TlsHelper.TLS_KEY, TlsHelper.getInstance().getTlsKeyBase64())
                    .addToData(TlsHelper.TLS_CRT, TlsHelper.getInstance().getTlsCertBase64())
                    .build();
            client.secrets().overwriteControllerSecret(secret);
            result.add(new VolumeBuilder().withName("tls-volume").withNewSecret()
                    .withSecretName(resource.getMetadata().getName() + "-controller-tls-secret").endSecret()
                    .build());
        }
        return result;
    }

    private List<VolumeMount> maybeCreateTlsVolumeMounts() {
        List<VolumeMount> result = new ArrayList<>();
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            result.add(new VolumeMountBuilder().withName("ca-cert-volume").withMountPath(ETC_ENTANDO_CA).build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new VolumeMountBuilder().withName("tls-volume").withMountPath(ETC_ENTANDO_TLS).build());
        }
        return result;
    }

}
