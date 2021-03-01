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

package org.entando.kubernetes.controller.support.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;

public final class EntandoOperatorConfig extends EntandoOperatorConfigBase {

    public static final String SEPERATOR_PATTERN = "[\\s,:]+";

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
    public static Optional<String> getEntandoDockerImageInfoNamespace() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_INFO_NAMESPACE);
    }

    public static Optional<String> getOperatorServiceAccount() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SERVICEACCOUNT);
    }

    public static OperatorDeploymentType getOperatorDeploymentType() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE)
                .map(OperatorDeploymentType::resolve)
                .orElse(OperatorDeploymentType.HELM);
    }

    public static boolean isClusterScopedDeployment() {
        if (getOperatorDeploymentType() == OperatorDeploymentType.OLM) {
            return getNamespacesToObserve().isEmpty();
        } else {
            return getNamespacesToObserve().stream().anyMatch("*"::equals);
        }
    }

    public static List<String> getNamespacesToObserve() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE).map(s -> s.split(SEPERATOR_PATTERN))
                .map(Arrays::asList)
                .orElse(new ArrayList<>());
    }

    public static List<String> getImagePullSecrets() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS).map(s -> s.split(SEPERATOR_PATTERN))
                .map(Arrays::asList)
                .orElse(new ArrayList<>());
    }

    public static SecurityMode getOperatorSecurityMode() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE)
                .map(SecurityMode::resolve).orElse(SecurityMode.LENIENT);
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

    public static Optional<String> getPullPolicyOverride() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_PULL_POLICY_OVERRIDE);
    }
    /*
    Misc config
     */

    public static boolean requiresFilesystemGroupOverride() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE).map("true"::equals).orElse(false);
    }

    public static Optional<String> getIngressClass() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_INGRESS_CLASS);
    }

    public static Optional<String> getDefaultRoutingSuffix() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_DEFAULT_ROUTING_SUFFIX);
    }

    public static long getPodCompletionTimeoutSeconds() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }

    public static long getPodReadinessTimeoutSeconds() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS).map(Long::valueOf).orElse(600L);
    }

    public static long getPodShutdownTimeoutSeconds() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_POD_SHUTDOWN_TIMEOUT_SECONDS).map(Long::valueOf).orElse(120L);
    }

    public static boolean imposeResourceLimits() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_DEFAULT_LIMITS).map(Boolean::valueOf).orElse(true);
    }

    public static boolean disablePvcGarbageCollection() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION).map(Boolean::valueOf)
                .orElse(false);
    }

    public static boolean garbageCollectSuccessfullyCompletedPods() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_GC_CONTROLLER_PODS).map(Boolean::valueOf)
                .orElse(false);
    }

    public static Optional<String> getCertificateAuthoritySecretName() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_CA_SECRET_NAME);
    }

    public static Optional<String> getTlsSecretName() {
        return lookupProperty(EntandoOperatorConfigProperty.ENTANDO_TLS_SECRET_NAME);

    }
}
