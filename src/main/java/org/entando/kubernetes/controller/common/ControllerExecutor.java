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

package org.entando.kubernetes.controller.common;

import static java.util.Optional.ofNullable;

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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

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
                client.secrets().loadControllerConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()));
    }

    private static Map<String, String> buildImageMap() {
        Map<String, String> map = new ConcurrentHashMap<>();
        map.put("EntandoKeycloakServer", "entando-k8s-keycloak-controller");
        map.put("EntandoClusterInfrastructure", "entando-k8s-cluster-infrastructure-controller");
        map.put("EntandoPlugin", "entando-k8s-plugin-controller");
        map.put("EntandoApp", "entando-k8s-app-controller");
        map.put("EntandoAppPluginLink", "entando-k8s-app-plugin-link-controller");
        map.put("EntandoCompositeApp", "entando-k8s-composite-app-controller");
        map.put("EntandoDatabaseService", "entando-k8s-database-service-controller");
        return map;
    }

    public static <T extends Serializable> String resolveControllerImageName(EntandoBaseCustomResource<T> resource) {
        return resolveControllerImageNameByKind(resource.getKind());
    }

    public static <T extends EntandoBaseCustomResource<?>> String resolveControllerImageName(Class<T> type) {
        String kind = KubeUtils.getKindOf(type);
        return resolveControllerImageNameByKind(kind);
    }

    private static String resolveControllerImageNameByKind(String kind) {
        return resourceKindToImageNames.get(kind);
    }

    public <T extends Serializable> Pod startControllerFor(Action action, EntandoBaseCustomResource<T> resource, String imageVersionToUse) {
        removeObsoleteControllerPods(resource);
        Pod pod = buildControllerPod(action, resource, imageVersionToUse);
        return client.pods().start(pod);
    }

    public <T extends EntandoDeploymentSpec> Pod runControllerFor(Action action, EntandoBaseCustomResource<T> resource,
            String imageVersionToUse) {
        removeObsoleteControllerPods(resource);
        Pod pod = buildControllerPod(action, resource, imageVersionToUse);
        return client.pods().runToCompletion(pod);
    }

    private void removeObsoleteControllerPods(EntandoBaseCustomResource<?> resource) {
        this.client.pods().removeAndWait(controllerNamespace, Map.of(
                KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resource.getKind(),
                KubeUtils.ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME, resource.getMetadata().getNamespace(),
                resource.getKind(), resource.getMetadata().getName()));
    }

    private <T extends Serializable> Pod buildControllerPod(Action action, EntandoBaseCustomResource<T> resource,
            String imageVersionToUse) {
        return new PodBuilder().withNewMetadata()
                .withName(resource.getMetadata().getName() + "-deployer-" + RandomStringUtils.randomAlphanumeric(10).toLowerCase())
                .withNamespace(this.controllerNamespace)
                .addToLabels(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resource.getKind())
                .addToLabels(KubeUtils.ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME, resource.getMetadata().getNamespace())
                .addToLabels(resource.getKind(), resource.getMetadata().getName())
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withServiceAccountName(determineServiceAccountName())
                .addNewContainer()
                .withName("deployer")
                .withImage(determineControllerImage(resource, imageVersionToUse))
                .withImagePullPolicy("IfNotPresent")
                .withEnv(buildEnvVars(action, resource))
                .withVolumeMounts(maybeCreateTlsVolumeMounts())
                .endContainer()
                .withVolumes(maybeCreateTlsVolumes(resource))
                .endSpec()
                .build();
    }

    private String determineServiceAccountName() {
        return EntandoOperatorConfig.getOperatorServiceAccount().orElse("default");
    }

    private <T extends Serializable> String determineControllerImage(EntandoBaseCustomResource<T> resource, String imageVersionToUse) {
        return this.imageResolver.determineImageUri(
                "entando/" + resolveControllerImageName(resource) + ofNullable(imageVersionToUse).map(s -> ":" + s).orElse(""));
    }

    private <T extends Serializable> List<EnvVar> buildEnvVars(Action action, EntandoBaseCustomResource<T> resource) {
        ArrayList<EnvVar> result = new ArrayList<>();
        result.add(new EnvVar("ENTANDO_RESOURCE_ACTION", action.name(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAMESPACE", resource.getMetadata().getNamespace(), null));
        result.add(new EnvVar("ENTANDO_RESOURCE_NAME", resource.getMetadata().getName(), null));
        result.addAll(Stream.of(EntandoOperatorConfigProperty.values())
                .filter(prop -> EntandoOperatorConfigBase.lookupProperty(prop).isPresent())
                .map(prop -> new EnvVar(prop.name(), EntandoOperatorConfigBase.lookupProperty(prop).get(), null))
                .collect(Collectors.toList()));
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            //TODO no need to propagate the raw CA certs. But we do need to mount the resulting Java Truststore and override the
            // _JAVA_OPTS variable
            StringBuilder sb = new StringBuilder();
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path ->
                    sb.append(ETC_ENTANDO_CA).append("/").append(path.getFileName().toString()).append(" "));
            result.add(new EnvVar(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.name(), sb.toString().trim(), null));
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            //TODO no need to propagate the Tls certs.
            result.add(new EnvVar(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.name(), ETC_ENTANDO_TLS, null));
        }
        System.getProperties().entrySet().stream()
                .filter(objectObjectEntry -> objectObjectEntry.getKey().toString().toLowerCase(Locale.ROOT).replaceAll("_", ".")
                        .startsWith("related.image")).forEach(objectObjectEntry -> result
                .add(new EnvVar(objectObjectEntry.getKey().toString().toUpperCase(Locale.ROOT).replaceAll(".", "_"),
                        objectObjectEntry.getValue().toString(), null)));
        System.getenv().entrySet().stream()
                .filter(objectObjectEntry -> objectObjectEntry.getKey().startsWith("RELATED_IMAGE")).forEach(objectObjectEntry -> result
                .add(new EnvVar(objectObjectEntry.getKey(),
                        objectObjectEntry.getValue(), null)));
        return result;
    }

    private <T extends Serializable> List<Volume> maybeCreateTlsVolumes(EntandoBaseCustomResource<T> resource) {
        List<Volume> result = new ArrayList<>();
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            //TODO no need to propagate the raw CA certs. But we do need to mount the resulting Java Truststore and override the
            // _JAVA_OPTS var
            Secret secret = new SecretBuilder().withNewMetadata().withName(resource.getMetadata().getName() + "-controller-ca-cert-secret")
                    .endMetadata().withData(new ConcurrentHashMap<>()).build();
            //Add all available CA Certs. No need to map the trustStore itself - the controller will build this up internally
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path -> secret.getData()
                    .put(path.getFileName().toString(), TlsHelper.getInstance().getTlsCaCertBase64(path)));
            client.secrets().overwriteControllerSecret(secret);
            result.add(new VolumeBuilder().withName("ca-cert-volume").withNewSecret()
                    .withSecretName(resource.getMetadata().getName() + "-controller-ca-cert-secret").endSecret()
                    .build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            //TODO no need to propagate the Tls certs. It can just be mounted in deployments
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
