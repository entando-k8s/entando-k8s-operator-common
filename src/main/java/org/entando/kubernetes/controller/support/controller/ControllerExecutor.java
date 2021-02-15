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

package org.entando.kubernetes.controller.support.controller;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.common.TlsHelper;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ControllerExecutor {

    public static final String ETC_ENTANDO_TLS = TlsAware.ETC_ENTANDO_TLS;
    public static final String ETC_ENTANDO_CA = TlsAware.ETC_ENTANDO_CA;

    private static final Map<String, String> resourceKindToImageNames = buildImageMap();
    private final SimpleK8SClient<?> client;
    private final EntandoImageResolver imageResolver;
    private final String controllerNamespace;

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

    public static String resolveControllerImageName(EntandoCustomResource resource) {
        return resolveControllerImageNameByKind(resource.getKind());
    }

    private static String resolveControllerImageNameByKind(String kind) {
        return resourceKindToImageNames.get(kind);
    }

    public Pod startControllerFor(Action action, EntandoCustomResource resource, String imageVersionToUse) {
        removeObsoleteControllerPods(resource);
        Pod pod = buildControllerPod(action, resource, imageVersionToUse);
        return client.pods().start(pod);
    }

    public Pod runControllerFor(Action action, EntandoCustomResource resource, String imageVersionToUse) {
        removeObsoleteControllerPods(resource);
        Pod pod = buildControllerPod(action, resource, imageVersionToUse);
        return client.pods().runToCompletion(pod);
    }

    private void removeObsoleteControllerPods(EntandoCustomResource resource) {
        this.client.pods().removeAndWait(controllerNamespace, Map.of(
                KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resource.getKind(),
                KubeUtils.ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME, resource.getMetadata().getNamespace(),
                resource.getKind(), resource.getMetadata().getName()));
    }

    private Pod buildControllerPod(Action action, EntandoCustomResource resource,
            String imageVersionToUse) {
        return new PodBuilder().withNewMetadata()
                .withName(resource.getMetadata().getName() + "-deployer-" + NameUtils.randomNumeric(4).toLowerCase())
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

    private String determineControllerImage(EntandoCustomResource resource, String imageVersionToUse) {
        return this.imageResolver.determineImageUri(
                "entando/" + resolveControllerImageName(resource) + ofNullable(imageVersionToUse).map(s -> ":" + s).orElse(""));
    }

    private void addTo(Map<String, EnvVar> result, EnvVar envVar) {
        result.put(envVar.getName(), envVar);
    }

    private List<EnvVar> buildEnvVars(Action action, EntandoCustomResource resource) {
        Map<String, EnvVar> result = new HashMap<>();
        //TODO test if we can remove this line now given that we are copying all system properties and environment variables.
        Stream.of(EntandoOperatorConfigProperty.values())
                .filter(prop -> EntandoOperatorConfigBase.lookupProperty(prop).isPresent())
                .forEach(prop -> addTo(result, new EnvVar(prop.name(), EntandoOperatorConfigBase.lookupProperty(prop).get(), null)));
        System.getProperties().entrySet().stream()
                .filter(this::matchesKnownSystemProperty).forEach(objectObjectEntry -> addTo(result,
                new EnvVar(objectObjectEntry.getKey().toString().toUpperCase(Locale.ROOT).replace(".", "_").replace("-", "_"),
                        objectObjectEntry.getValue().toString(), null)));
        System.getenv().entrySet().stream()
                .filter(this::matchesKnownEnvironmentVariable)
                .forEach(objectObjectEntry -> addTo(result, new EnvVar(objectObjectEntry.getKey(),
                        objectObjectEntry.getValue(), null)));
        if (!EntandoOperatorConfig.getCertificateAuthorityCertPaths().isEmpty()) {
            //TODO no need to propagate the raw CA certs. But we do need to mount the resulting Java Truststore and override the
            // _JAVA_OPTS variable
            StringBuilder sb = new StringBuilder();
            EntandoOperatorConfig.getCertificateAuthorityCertPaths().forEach(path ->
                    sb.append(TlsAware.ETC_ENTANDO_CA).append("/").append(path.getFileName().toString()).append(" "));
            addTo(result, new EnvVar(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.name(), sb.toString().trim(), null));
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            //TODO no need to propagate the Tls certs.
            addTo(result, new EnvVar(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.name(), TlsAware.ETC_ENTANDO_TLS, null));
        }
        //Make sure we overwrite previously set resource info
        addTo(result, new EnvVar("ENTANDO_RESOURCE_ACTION", action.name(), null));
        addTo(result, new EnvVar("ENTANDO_RESOURCE_NAMESPACE", resource.getMetadata().getNamespace(), null));
        addTo(result, new EnvVar("ENTANDO_RESOURCE_NAME", resource.getMetadata().getName(), null));
        return new ArrayList<>(result.values());
    }

    private boolean matchesKnownEnvironmentVariable(Map.Entry<String, String> objectObjectEntry) {
        return objectObjectEntry.getKey().startsWith("RELATED_IMAGE") || objectObjectEntry.getKey().startsWith("ENTANDO_");
    }

    private boolean matchesKnownSystemProperty(Map.Entry<Object, Object> objectObjectEntry) {
        String propertyName = objectObjectEntry.getKey().toString().toLowerCase(Locale.ROOT).replace("_", ".");
        return propertyName.startsWith("related.image") || propertyName.startsWith("entando.");
    }

    private List<Volume> maybeCreateTlsVolumes(EntandoCustomResource resource) {
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
            result.add(new VolumeMountBuilder().withName("ca-cert-volume").withMountPath(TlsAware.ETC_ENTANDO_CA).build());
        }
        if (TlsHelper.isDefaultTlsKeyPairAvailable()) {
            result.add(new VolumeMountBuilder().withName("tls-volume").withMountPath(TlsAware.ETC_ENTANDO_TLS).build());
        }
        return result;
    }

}
