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

package org.entando.kubernetes.controller.support.client.impl;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MINOR_VERSION = 20;

    private final KubernetesClient client;

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public boolean supportsStartupProbes() {
        try {
            K8sVersionInfo version = fetchVersionInfo();
            // Return true if version is null (mock server)
            return version == null || parseMinorVersion(version.getMinor()) >= 16;
        } catch (JsonProcessingException | ExecutionException | InterruptedException | NullPointerException e) {
            // Fallback: assume modern K8s (>= 1.16) supports startup probes
            return true;
        }
    }

    /**
     * Fetches Kubernetes version info using raw HTTP call to bypass Fabric8's VersionInfo
     * which doesn't support new fields (emulationMajor, emulationMinor, etc.) added in K8s 1.32+
     * as part of KEP-4330.
     */
    private K8sVersionInfo fetchVersionInfo() throws JsonProcessingException, ExecutionException, InterruptedException {
        HttpClient httpClient = client.getHttpClient();
        String masterUrl = client.getConfiguration().getMasterUrl();
        // Remove trailing slash if present
        if (masterUrl.endsWith("/")) {
            masterUrl = masterUrl.substring(0, masterUrl.length() - 1);
        }

        HttpRequest request = httpClient.newHttpRequestBuilder()
                .uri(URI.create(masterUrl + "/version"))
                .build();

        HttpResponse<String> response = httpClient.sendAsync(request, String.class).get();

        if (response.isSuccessful() && response.body() != null) {
            return OBJECT_MAPPER.readValue(response.body(), K8sVersionInfo.class);
        }
        return null;
    }

    private int parseMinorVersion(String minor) {
        if (minor == null || minor.isEmpty()) {
            return DEFAULT_MINOR_VERSION;
        }
        StringBuilder sb = new StringBuilder();
        // Some versions have trailing non-digit characters (e.g., "16+")
        for (char current : minor.toCharArray()) {
            if (Character.isDigit(current)) {
                sb.append(current);
            } else {
                break;
            }
        }
        return sb.length() > 0 ? Integer.parseInt(sb.toString()) : DEFAULT_MINOR_VERSION;
    }

    /**
     * Custom version info class that ignores unknown fields like emulationMajor, emulationMinor,
     * minCompatibilityMajor, minCompatibilityMinor added in Kubernetes 1.32+ (KEP-4330).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class K8sVersionInfo {
        private String major;
        private String minor;
        private String gitVersion;

        public String getMajor() {
            return major;
        }

        public void setMajor(String major) {
            this.major = major;
        }

        public String getMinor() {
            return minor;
        }

        public void setMinor(String minor) {
            this.minor = minor;
        }

        public String getGitVersion() {
            return gitVersion;
        }

        public void setGitVersion(String gitVersion) {
            this.gitVersion = gitVersion;
        }
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace, Deployment deployment, int timeoutSeconds)
            throws TimeoutException {
        Deployment existingDeployment = getDeploymenResourceFor(peerInNamespace, deployment).get();
        if (existingDeployment == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            //Don't wait because the polling in Fabric8 is dodge
            getDeploymenResourceFor(peerInNamespace, deployment).scale(0, true);
            FilterWatchListDeletable<Pod, PodList, PodResource> podResource = client.pods()
                    .inNamespace(existingDeployment.getMetadata().getNamespace())
                    .withLabelSelector(existingDeployment.getSpec().getSelector());
            interruptionSafe(() -> DefaultPodClient.waitUntilCondition(
                    podResource,
                    //pod -> podResource.list().getItems().isEmpty(),
                    Objects::isNull,
                    timeoutSeconds,
                    TimeUnit.SECONDS)
            );

            //Get the latest version after scaling to avoid resourceVersion conflict
            Deployment latest = getDeploymenResourceFor(peerInNamespace, deployment).get();
            //Apply our desired spec to the latest version
            latest.setSpec(deployment.getSpec());
            //Create the deployment with the correct replicas now. We don't support 0 because we will be waiting for the pod
            return getDeploymenResourceFor(peerInNamespace, deployment).patch(latest);
        }
    }

    private RollableScalableResource<Deployment> getDeploymenResourceFor(
            EntandoCustomResource peerInNamespace,
            Deployment deployment) {
        return client.apps()
                .deployments()
                .inNamespace(peerInNamespace.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName());
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
