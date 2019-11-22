package org.entando.kubernetes.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EntandoOperatorConfig extends EntandoOperatorConfigBase {

    public static final String ENTANDO_CA_CERT_PATHS = "entando.ca.cert.paths";
    public static final String ENTANDO_USE_AUTO_CERT_GENERATION = "entando.use.auto.cert.generation";
    public static final String ENTANDO_PATH_TO_TLS_KEYPAIR = "entando.path.to.tls.keypair";
    public static final String ENTANDO_K8S_IMAGE_VERSION = "entando.k8s.image.version";
    public static final String ENTANDO_K8S_OPERATOR_REGISTRY = "entando.k8s.operator.registry";
    public static final String ENTANDO_DEFAULT_ROUTING_SUFFIX = "entando.default.routing.suffix";
    public static final String ENTANDO_K8S_OPERATOR_IMAGE_NAMESPACE = "entando.k8s.operator.image.namespace";
    public static final String ENTANDO_K8S_OPERATOR_SCOPE = "entando.k8s.operator.scope";
    public static final String ENTANDO_K8S_OPERATOR_SECURITY_MODE = "entando.k8s.operator.security.mode";
    public static final String ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS = "entando.pod.completion.timeout.seconds";
    public static final String ENTANDO_POD_READINESS_TIMEOUT_SECONDS = "entando.pod.readiness.timeout.seconds";
    public static final String ENTANDO_OPERATOR_NAMESPACE_OVERRIDE = "entando.operator.namespace.override";

    private EntandoOperatorConfig() {

    }

    public static String getEntandoImageVersion() {
        return getProperty(ENTANDO_K8S_IMAGE_VERSION, "6.0.0-SNAPSHOT");
    }

    public static String getEntandoDockerRegistry() {
        return getProperty(ENTANDO_K8S_OPERATOR_REGISTRY, "docker.io");
    }

    public static Optional<String> getOperatorNamespaceOverride() {
        return lookupProperty(ENTANDO_OPERATOR_NAMESPACE_OVERRIDE);
    }

    public static boolean disableKeycloakSslRequirement() {
        return Boolean.valueOf(lookupProperty("entando.disable.keycloak.ssl.requirement").orElse("false"));
    }

    public static boolean useAutoCertGeneration() {
        return Boolean.valueOf(lookupProperty(ENTANDO_USE_AUTO_CERT_GENERATION).orElse("false"));
    }

    public static List<Path> getCertificateAuthorityCertPaths() {
        String[] paths = getProperty(ENTANDO_CA_CERT_PATHS, "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt").split("\\s+");
        List<Path> result = new ArrayList<>();
        for (String path : paths) {
            if (Paths.get(path).toFile().exists()) {
                result.add(Paths.get(path));
            }

        }
        return result;
    }

    public static Optional<Path> getPathToDefaultTlsKeyPair() {
        return lookupProperty(ENTANDO_PATH_TO_TLS_KEYPAIR).filter(s -> Paths.get(s).toFile().exists()).map(Paths::get);
    }

    public static Optional<String> getDefaultRoutingSuffix() {
        return lookupProperty(ENTANDO_DEFAULT_ROUTING_SUFFIX);
    }

    public static String getEntandoImageNamespace() {
        return getProperty(ENTANDO_K8S_OPERATOR_IMAGE_NAMESPACE, "entando");
    }

    public static SecurityMode getOperatorSecurityMode() {
        return SecurityMode.caseInsensitiveValueOf(getProperty(ENTANDO_K8S_OPERATOR_SECURITY_MODE, "lenient"));
    }

    public static OperatorScope getOperatorScope() {
        return OperatorScope.caseInsensitiveValueOf(getProperty(ENTANDO_K8S_OPERATOR_SCOPE, "cluster"));
    }

    public static String getEntandoInfrastructureSecretName() {
        return getProperty("entando.cluster.infrastructure.secret.name", "entando-cluster-infrastructure-secret");
    }

    public static String getDefaultKeycloakSecretName() {
        return getProperty("entando.default.keycloak.secret.name", "keycloak-admin-secret");
    }

    public static long getPodCompletionTimeoutSeconds() {
        return lookupProperty(ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }

    public static long getPodReadinessTimeoutSeconds() {
        return lookupProperty(ENTANDO_POD_READINESS_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }
}
