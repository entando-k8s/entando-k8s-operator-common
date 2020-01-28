package org.entando.kubernetes.controller;

public enum EntandoOperatorConfigProperty {
    /*
    Config to resolve Entando Docker Images
     */
    ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK("entando.docker.image.version.fallback"),
    ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE("entando.docker.image.override"),
    ENTANDO_DOCKER_REGISTRY_FALLBACK("entando.docker.registry.fallback"),
    ENTANDO_DOCKER_REGISTRY_OVERRIDE("entando.docker.registry.override"),
    ENTANDO_DOCKER_IMAGE_ORG_FALLBACK("entando.docker.image.org.fallback"),
    ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE("entando.docker.image.org.override"),
    ENTANDO_DOCKER_IMAGE_INFO_CONFIGMAP("entando.docker.image.info.configmap"),

    /*
    K8S Operator operational config
     */
    ENTANDO_K8S_OPERATOR_CONFIGMAP_NAMESPACE("entando.k8s.operator.configmap.namespace"),
    ENTANDO_K8S_OPERATOR_SCOPE("entando.k8s.operator.scope"),
    ENTANDO_K8S_OPERATOR_SECURITY_MODE("entando.k8s.operator.security.mode"),
    ENTANDO_K8S_OPERATOR_NAMESPACE_TO_OBSERVE("entando.k8s.operator.namespace.to.observe"),
    ENTANDO_K8S_OPERATOR_SERVICEACCOUNT("entando.k8s.operator.serviceaccount"),

    /*
    TLS config
     */
    ENTANDO_CA_CERT_PATHS("entando.ca.cert.paths"),
    ENTANDO_USE_AUTO_CERT_GENERATION("entando.use.auto.cert.generation"),
    ENTANDO_PATH_TO_TLS_KEYPAIR("entando.path.to.tls.keypair"),
    ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT("entando.disable.keycloak.ssl.requirement"),
    /*
    Misc config
     */
    ENTANDO_DEFAULT_ROUTING_SUFFIX("entando.default.routing.suffix"),
    ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS("entando.pod.completion.timeout.seconds"),
    ENTANDO_POD_READINESS_TIMEOUT_SECONDS("entando.pod.readiness.timeout.seconds"),
    ENTANDO_CLUSTER_INFRASTRUCTURE_SECRET_NAME("entando.cluster.infrastructure.secret.name"),
    ENTANDO_DEFAULT_KEYCLOAK_SECRET_NAME("entando.default.keycloak.secret.name");

    private final String jvmSystemProperty;

    private EntandoOperatorConfigProperty(String jvmSystemProperty) {
        this.jvmSystemProperty = jvmSystemProperty;
    }

    public String getJvmSystemProperty() {
        return jvmSystemProperty;
    }
}
