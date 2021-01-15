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

import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.mockito.Mockito;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
@SuppressWarnings({"java:S6068", "java:S6073"})
public class BareBonesDeployableMockClientTest extends BareBonesDeployableTestBase {

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

    @Override
    public SimpleK8SClient getClient() {
        return simpleK8SClientDouble;
    }

}
