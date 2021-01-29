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

package org.entando.kubernetes.controller.inprocesstest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.mockito.Mockito;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
//Because Sonar cannot detect that the test methods are declared in the superclass
//and it cannot detect custom matchers and captors
@SuppressWarnings({"java:S6068", "java:S6073","java:S2187"})
public class ContainerUsingExternalDatabaseMockClientTest extends ContainerUsingExternalDatabaseTestBase {

    private SimpleK8SClientDouble simpleK8SClientDouble = new SimpleK8SClientDouble();
    private SimpleKeycloakClient keycloakClient = Mockito.mock(SimpleKeycloakClient.class);

    @BeforeAll
    public static void emulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(true);

    }

    @AfterAll
    public static void dontEmulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(false);
    }

    @BeforeEach
    public void prepareKeycloakMocks() {
        lenient().when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn("ASDFASDFASDfa");

    }

    @Override
    public SimpleK8SClient getClient() {
        return simpleK8SClientDouble;
    }

    @Override
    protected SimpleKeycloakClient getKeycloakClient() {
        return keycloakClient;
    }
}
