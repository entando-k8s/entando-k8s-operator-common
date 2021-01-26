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

package org.entando.kubernetes.controller.unittest.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.entando.kubernetes.controller.common.examples.barebones.BareBonesContainer;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.creators.ResourceCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ResourceCalculatorTest {

    @AfterEach
    @BeforeEach
    void resetProvidedRatio() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty());
    }

    @Test
    void calculateRequestsWithDefaultRatio() {
        ResourceCalculator resourceCalculator = new ResourceCalculator(new BareBonesContainer());
        assertThat(resourceCalculator.getCpuLimit(), is("800m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("256Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("80m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("25.6Mi"));
    }

    @Test
    void calculateRequestsWithProvidedRatio() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ResourceCalculator resourceCalculator = new ResourceCalculator(new BareBonesContainer());
        assertThat(resourceCalculator.getCpuLimit(), is("800m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("256Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("160m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("51.2Mi"));
    }
}
