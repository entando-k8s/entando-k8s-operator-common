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

package org.entando.kubernetes.test.componenttest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.mockito.Mockito;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
@SuppressWarnings({"java:S6068", "java:S6073"})
public class SpringBootContainerMockClientTest extends SpringBootContainerTestBase {

    private SimpleK8SClientDouble simpleK8SClientDouble = new SimpleK8SClientDouble();
    private SimpleKeycloakClient keycloakClient = Mockito.mock(SimpleKeycloakClient.class);

    @AfterEach
    public void dontEmulatePodWaiting() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        getClient().pods().getPodWatcherQueue().clear();
    }

    @BeforeEach
    public void prepareKeycloakMocks() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
        lenient().when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn("ASDFASDFASDfa");

    }

    @Override
    public SimpleK8SClientDouble getClient() {
        return simpleK8SClientDouble;
    }

    @Override
    protected SimpleKeycloakClient getKeycloakClient() {
        return keycloakClient;
    }
}
