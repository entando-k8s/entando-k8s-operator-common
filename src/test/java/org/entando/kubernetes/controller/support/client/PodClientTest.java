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

package org.entando.kubernetes.controller.support.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;
import org.entando.kubernetes.client.EntandoExecListener;
import org.entando.kubernetes.controller.support.client.doubles.PodClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.PodResourceDouble;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class PodClientTest {

    @Test
    void testExec() throws IOException {
        PodClientDouble podClientDouble = new PodClientDouble(new HashMap<>());
        PodResourceDouble podResource = new PodResourceDouble();
        EntandoExecListener execWatchDouble = podClientDouble
                .executeAndWait(podResource, "some-container", 10, "echo hello world");
        PodResourceDouble resultingPodResource = (PodResourceDouble) execWatchDouble.getExecable();
        assertThat(resultingPodResource.getContext().getContainerId(), is("some-container"));
        byte[] bytes = IOUtils.readFully(resultingPodResource.getContext().getIn(), resultingPodResource.getContext().getIn().available());
        assertThat(new String(bytes), is("echo hello world\nexit 0\n"));
        assertThat(resultingPodResource.getContext().isTty(), is(true));
    }
}
