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

package org.entando.kubernetes.controller;

public enum EntandoOperatorConfigProperty {
    /*
    Config to resolve Entando Docker Images
     */
    ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK("entando.docker.image.version.fallback"),
    ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE("entando.docker.image.version.override"),
    ENTANDO_DOCKER_REGISTRY_FALLBACK("entando.docker.registry.fallback"),
    ENTANDO_DOCKER_REGISTRY_OVERRIDE("entando.docker.registry.override"),
    ENTANDO_DOCKER_IMAGE_ORG_FALLBACK("entando.docker.image.org.fallback"),
    ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE("entando.docker.image.org.override"),
    ENTANDO_DOCKER_IMAGE_INFO_CONFIGMAP("entando.docker.image.info.configmap"),

    /*
    K8S Operator operational config
     */
    // the specified namespace is used to find the config-map from which create the entando app
    ENTANDO_K8S_OPERATOR_CONFIGMAP_NAMESPACE("entando.k8s.operator.configmap.namespace"),
    ENTANDO_K8S_OPERATOR_SCOPE("entando.k8s.operator.scope"),
    ENTANDO_K8S_OPERATOR_SECURITY_MODE("entando.k8s.operator.security.mode"),
    ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS("entando.k8s.operator.image.pull.secrets"),
    ENTANDO_K8S_OPERATOR_ID("entando.k8s.operator.id"),
    ENTANDO_K8S_OPERATOR_API_VERSION_RANGE("entando.k8s.operator.api.version.range"),
    ENTANDO_K8S_OPERATOR_NAMESPACE_TO_OBSERVE("entando.k8s.operator.namespace.to.observe"),
    ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION("entando.k8s.operator.disable.pvc.garbage.collection"),
    ENTANDO_NAMESPACES_TO_OBSERVE("entando.namespaces.to.observe"),
    ENTANDO_K8S_OPERATOR_SERVICEACCOUNT("entando.k8s.operator.serviceaccount"),
    ENTANDO_K8S_OPERATOR_IMPOSE_DEFAULT_LIMITS("entando.k8s.operator.impose.default.limits"),
    ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO("entando.k8s.operator.request.to.limit.ratio"),
    ENTANDO_K8S_OPERATOR_FORCE_DB_PASSWORD_RESET("entando.k8s.operator.force.db.password.reset"),

    /*
    TLS config
     */
    ENTANDO_CA_CERT_PATHS("entando.ca.cert.paths"),
    ENTANDO_CA_CERT_ROOT_FOLDER("entando.ca.cert.root.folder"),
    ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER("entando.assume.external.https.provider"),
    ENTANDO_USE_AUTO_CERT_GENERATION("entando.use.auto.cert.generation"),
    ENTANDO_PATH_TO_TLS_KEYPAIR("entando.path.to.tls.keypair"),
    ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT("entando.disable.keycloak.ssl.requirement"),
    /*
    Misc config
     */
    // creates a dns mapping from the specified hostname *.*.*.*.nip.io to the corresponding ip *.*.*.* . typically it should point to
    // hosting machine ip address (https://nip.io/)
    ENTANDO_DEFAULT_ROUTING_SUFFIX("entando.default.routing.suffix"),
    ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS("entando.pod.completion.timeout.seconds"),
    ENTANDO_POD_READINESS_TIMEOUT_SECONDS("entando.pod.readiness.timeout.seconds"),
    ENTANDO_POD_SHUTDOWN_TIMEOUT_SECONDS("entando.pod.shutdown.timeout.seconds"),
    ENTANDO_CLUSTER_INFRASTRUCTURE_SECRET_NAME("entando.cluster.infrastructure.secret.name"),
    ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE("entando.requires.filesystem.group.override"),
    ENTANDO_INGRESS_CLASS("entando.ingress.class"),
    ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK("entando.force.external.access.to.keycloak");

    private final String jvmSystemProperty;

    EntandoOperatorConfigProperty(String jvmSystemProperty) {
        this.jvmSystemProperty = jvmSystemProperty;
    }

    public String getJvmSystemProperty() {
        return jvmSystemProperty;
    }
}
