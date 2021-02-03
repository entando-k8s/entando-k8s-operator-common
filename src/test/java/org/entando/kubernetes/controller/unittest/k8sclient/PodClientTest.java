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

package org.entando.kubernetes.controller.unittest.k8sclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.integrationtest.support.PodResourceDouble;
import org.entando.kubernetes.controller.integrationtest.support.PodResourceDouble.ExecWatchDouble;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class PodClientTest {

    @Test
    void testExec() throws IOException {
        PodClientDouble podClientDouble = new PodClientDouble(new HashMap<>());
        PodResourceDouble podResource = new PodResourceDouble();
        ExecWatchDouble execWatchDouble = (ExecWatchDouble) podClientDouble
                .executeAndWait(podResource, "some-container", 10, "echo hello world");
        assertThat(execWatchDouble.getPodResourceDouble().getContext().getContainerId(), is("some-container"));
        InputStream in = execWatchDouble.getPodResourceDouble().getContext().getIn();
        byte[] bytes = IOUtils.readFully(in, in.available());
        assertThat(new String(bytes), is("echo hello world\nexit 0\n"));
        assertThat(execWatchDouble.getPodResourceDouble().getContext().isTty(), is(true));
    }
}
