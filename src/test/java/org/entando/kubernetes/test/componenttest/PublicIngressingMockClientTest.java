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

import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
//Because Sonar doesn't pick up that the test methods are defined in the parent class
@SuppressWarnings({"java:S2187"})
public class PublicIngressingMockClientTest extends PublicIngressingTestBase {

    SimpleK8SClientDouble simpleK8SClientDouble = new SimpleK8SClientDouble();

    @BeforeEach
    public void emulatePodWaiting() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
    }

    @AfterEach
    public void dontEmulatePodWaiting() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        getClient().pods().getPodWatcherQueue().clear();
    }

    @Override
    public SimpleK8SClient<?> getClient() {
        return simpleK8SClientDouble;
    }

}
