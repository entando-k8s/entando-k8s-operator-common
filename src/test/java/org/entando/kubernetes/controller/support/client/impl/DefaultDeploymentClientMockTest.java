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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DefaultDeploymentClientMockTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Config config;

    @Mock
    private HttpRequest.Builder requestBuilder;

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private HttpResponse<String> httpResponse;

    private DefaultDeploymentClient deploymentClient;

    @BeforeEach
    void setUp() {
        deploymentClient = new DefaultDeploymentClient(kubernetesClient);
    }

    @Test
    void shouldSupportStartupProbesForModernKubernetes() throws Exception {
        // Given K8s version 1.20
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"20\",\"gitVersion\":\"v1.20.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldSupportStartupProbesForK8s116() throws Exception {
        // Given K8s version 1.16 (minimum version with startup probe support)
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"16\",\"gitVersion\":\"v1.16.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotSupportStartupProbesForOldKubernetes() throws Exception {
        // Given K8s version 1.15 (before startup probes)
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"15\",\"gitVersion\":\"v1.15.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleVersionWithTrailingCharacters() throws Exception {
        // Given K8s version with + suffix (e.g., GKE style "16+")
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"16+\",\"gitVersion\":\"v1.16.0-gke.1\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleK8s132WithNewVersionFields() throws Exception {
        // Given K8s 1.32+ with new fields (KEP-4330) that are ignored via @JsonIgnoreProperties
        String versionJson = "{\"major\":\"1\",\"minor\":\"32\",\"gitVersion\":\"v1.32.0\","
                + "\"emulationMajor\":\"1\",\"emulationMinor\":\"32\","
                + "\"minCompatibilityMajor\":\"1\",\"minCompatibilityMinor\":\"31\"}";
        setupHttpClientMock("https://kubernetes.default.svc/", versionJson);

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldSupportStartupProbesWhenVersionInfoIsNull() throws Exception {
        // Given HTTP response with null body
        setupHttpClientMockWithNullBody("https://kubernetes.default.svc/");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should return true as fallback (assume modern K8s)
        assertThat(result).isTrue();
    }

    @Test
    void shouldSupportStartupProbesWhenHttpCallFails() throws Exception {
        // Given HTTP call fails
        setupHttpClientMockWithFailure("https://kubernetes.default.svc/");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should return true as fallback (assume modern K8s)
        assertThat(result).isTrue();
    }

    @Test
    void shouldSupportStartupProbesWhenExceptionOccurs() {
        // Given client throws exception
        when(kubernetesClient.getHttpClient()).thenThrow(new NullPointerException("Connection failed"));

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should return true as fallback
        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleMasterUrlWithTrailingSlash() throws Exception {
        // Given master URL has trailing slash
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"20\",\"gitVersion\":\"v1.20.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleEmptyMinorVersion() throws Exception {
        // Given empty minor version
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"\",\"gitVersion\":\"v1.0.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should use DEFAULT_MINOR_VERSION (20) which is >= 16
        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleNullMinorVersion() throws Exception {
        // Given null minor version
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"gitVersion\":\"v1.0.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should use DEFAULT_MINOR_VERSION (20) which is >= 16
        assertThat(result).isTrue();
    }

    @Test
    void shouldParseVersionWithOnlyNonDigitCharacters() throws Exception {
        // Given minor version is only non-digit characters
        setupHttpClientMock("https://kubernetes.default.svc/", "{\"major\":\"1\",\"minor\":\"abc\",\"gitVersion\":\"v1.abc.0\"}");

        // When
        boolean result = deploymentClient.supportsStartupProbes();

        // Then - should use DEFAULT_MINOR_VERSION (20) which is >= 16
        assertThat(result).isTrue();
    }

    @Test
    void k8sVersionInfoShouldHaveProperGettersAndSetters() {
        // Given
        DefaultDeploymentClient.K8sVersionInfo versionInfo = new DefaultDeploymentClient.K8sVersionInfo();

        // When
        versionInfo.setMajor("1");
        versionInfo.setMinor("25");
        versionInfo.setGitVersion("v1.25.0");

        // Then
        assertThat(versionInfo.getMajor()).isEqualTo("1");
        assertThat(versionInfo.getMinor()).isEqualTo("25");
        assertThat(versionInfo.getGitVersion()).isEqualTo("v1.25.0");
    }

    private void setupHttpClientMock(String masterUrl, String responseBody) throws Exception {
        when(kubernetesClient.getHttpClient()).thenReturn(httpClient);
        when(kubernetesClient.getConfiguration()).thenReturn(config);
        when(config.getMasterUrl()).thenReturn(masterUrl);
        when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.uri(any(URI.class))).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(httpRequest);
        when(httpResponse.isSuccessful()).thenReturn(true);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpClient.sendAsync(eq(httpRequest), eq(String.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));
    }

    private void setupHttpClientMockWithNullBody(String masterUrl) throws Exception {
        when(kubernetesClient.getHttpClient()).thenReturn(httpClient);
        when(kubernetesClient.getConfiguration()).thenReturn(config);
        when(config.getMasterUrl()).thenReturn(masterUrl);
        when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.uri(any(URI.class))).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(httpRequest);
        when(httpResponse.isSuccessful()).thenReturn(true);
        when(httpResponse.body()).thenReturn(null);
        when(httpClient.sendAsync(eq(httpRequest), eq(String.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));
    }

    private void setupHttpClientMockWithFailure(String masterUrl) throws Exception {
        when(kubernetesClient.getHttpClient()).thenReturn(httpClient);
        when(kubernetesClient.getConfiguration()).thenReturn(config);
        when(config.getMasterUrl()).thenReturn(masterUrl);
        when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.uri(any(URI.class))).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(httpRequest);
        when(httpResponse.isSuccessful()).thenReturn(false);
        when(httpClient.sendAsync(eq(httpRequest), eq(String.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));
    }
}