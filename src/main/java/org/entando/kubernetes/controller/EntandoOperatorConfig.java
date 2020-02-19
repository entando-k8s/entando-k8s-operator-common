package org.entando.kubernetes.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class EntandoOperatorConfig extends EntandoOperatorConfigBase {

    private EntandoOperatorConfig() {
    }

    /*
    Config to resolve Entando Docker Images
     */

    public static String getEntandoDockerImageInfoConfigMap() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_INFO_CONFIGMAP).orElse("entando-docker-image-info");
    }

    /*
    K8S Operator operational config
     */
    public static Optional<String> getOperatorConfigMapNamespace() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_CONFIGMAP_NAMESPACE);
    }

    public static Optional<String> getOperatorServiceAccount() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SERVICEACCOUNT);
    }

    public static Optional<String> getOperatorNamespaceToObserve() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_NAMESPACE_TO_OBSERVE);
    }

    public static List<String> getNamespacesToObserve() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE).map(s -> s.split("\\,")).map(Arrays::asList)
                .orElse(new ArrayList<>());
    }

    public static SecurityMode getOperatorSecurityMode() {
        return SecurityMode
                .caseInsensitiveValueOf(getProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE, "lenient"));
    }

    /*
    TLS Config
     */
    public static boolean disableKeycloakSslRequirement() {
        return Boolean.parseBoolean(lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT).orElse("false"));
    }

    public static boolean useAutoCertGeneration() {
        return Boolean.parseBoolean(lookupProperty(EntandoOperatorConfigProperty.ENTANDO_USE_AUTO_CERT_GENERATION).orElse("false"));
    }

    public static List<Path> getCertificateAuthorityCertPaths() {
        String[] paths = getProperty(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS,
                "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt").split("\\s+");
        List<Path> result = new ArrayList<>();
        for (String path : paths) {
            if (Paths.get(path).toFile().exists()) {
                result.add(Paths.get(path));
            }

        }
        return result;
    }

    public static Optional<Path> getPathToDefaultTlsKeyPair() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR).filter(s -> Paths.get(s).toFile().exists())
                .map(Paths::get);
    }

    /*
    Git config
     */

    public static Optional<String> getDefaultGitUsername() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DEFAULT_GIT_USERNAME);
    }

    public static Optional<String> getDefaultGitToken() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DEFAULT_GIT_TOKEN);
    }

    /*
    Misc config
     */

    public static Optional<String> getDefaultRoutingSuffix() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DEFAULT_ROUTING_SUFFIX);
    }

    public static String getEntandoInfrastructureSecretName() {
        return getProperty(EntandoOperatorConfigProperty.ENTANDO_CLUSTER_INFRASTRUCTURE_SECRET_NAME,
                "entando-cluster-infrastructure-secret");
    }

    public static String getDefaultKeycloakSecretName() {
        return getProperty(EntandoOperatorConfigProperty.ENTANDO_DEFAULT_KEYCLOAK_SECRET_NAME, "keycloak-admin-secret");
    }

    public static long getPodCompletionTimeoutSeconds() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }

    public static long getPodReadinessTimeoutSeconds() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }
}
