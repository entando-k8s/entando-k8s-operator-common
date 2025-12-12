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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DefaultIngressClientMockTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private NonNamespaceOperation<Node, NodeList, Resource<Node>> nodesOperation;

    @Mock
    private NodeList nodeList;

    @Test
    void shouldResolveMasterHostnameFromMasterUrl() throws Exception {
        // Given a non-localhost master URL
        URL masterUrl = new URL("https://kubernetes.example.com:6443");
        when(kubernetesClient.getMasterUrl()).thenReturn(masterUrl);

        // When
        String result = DefaultIngressClient.resolveMasterHostname(kubernetesClient);

        // Then
        assertThat(result).isEqualTo("kubernetes.example.com");
    }

    @Test
    void shouldResolveNodeIpWhenMasterIsLocalhost() throws Exception {
        // Given localhost master URL
        URL masterUrl = new URL("https://127.0.0.1:6443");
        when(kubernetesClient.getMasterUrl()).thenReturn(masterUrl);

        Node node = new NodeBuilder()
                .withNewMetadata().withName("master-node").endMetadata()
                .withNewStatus()
                .addNewAddress()
                .withType("InternalIP")
                .withAddress("192.168.1.100")
                .endAddress()
                .endStatus()
                .build();

        when(kubernetesClient.nodes()).thenReturn(nodesOperation);
        when(nodesOperation.list()).thenReturn(nodeList);
        when(nodeList.getItems()).thenReturn(Collections.singletonList(node));

        // When
        String result = DefaultIngressClient.resolveMasterHostname(kubernetesClient);

        // Then
        assertThat(result).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldThrowExceptionWhenNoInternalIpFound() throws Exception {
        // Given localhost master URL but node has no InternalIP
        URL masterUrl = new URL("https://127.0.0.1:6443");
        when(kubernetesClient.getMasterUrl()).thenReturn(masterUrl);

        Node node = new NodeBuilder()
                .withNewMetadata().withName("master-node").endMetadata()
                .withNewStatus()
                .addNewAddress()
                .withType("Hostname")
                .withAddress("master-node")
                .endAddress()
                .endStatus()
                .build();

        when(kubernetesClient.nodes()).thenReturn(nodesOperation);
        when(nodesOperation.list()).thenReturn(nodeList);
        when(nodeList.getItems()).thenReturn(Collections.singletonList(node));

        // When/Then
        assertThatThrownBy(() -> DefaultIngressClient.resolveMasterHostname(kubernetesClient))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Impossible to retrieve node internal IP address");
    }

    @Test
    void shouldHandleMultipleNodeAddresses() throws Exception {
        // Given localhost master URL with node having multiple addresses
        URL masterUrl = new URL("https://127.0.0.1:6443");
        when(kubernetesClient.getMasterUrl()).thenReturn(masterUrl);

        Node node = new NodeBuilder()
                .withNewMetadata().withName("master-node").endMetadata()
                .withNewStatus()
                .addNewAddress()
                .withType("Hostname")
                .withAddress("master-node.local")
                .endAddress()
                .addNewAddress()
                .withType("ExternalIP")
                .withAddress("203.0.113.50")
                .endAddress()
                .addNewAddress()
                .withType("InternalIP")
                .withAddress("10.0.0.5")
                .endAddress()
                .endStatus()
                .build();

        when(kubernetesClient.nodes()).thenReturn(nodesOperation);
        when(nodesOperation.list()).thenReturn(nodeList);
        when(nodeList.getItems()).thenReturn(Collections.singletonList(node));

        // When
        String result = DefaultIngressClient.resolveMasterHostname(kubernetesClient);

        // Then - should pick InternalIP
        assertThat(result).isEqualTo("10.0.0.5");
    }
}