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

import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.common.ConfigurableResourceCalculator;
import org.entando.kubernetes.controller.common.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.app.EntandoAppSpecBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
public class ConfigurableResourceCalculatorTest {

    @AfterEach
    @BeforeEach
    public void resetProvidedRatio() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty());
    }

    @Test
    public void calculateRequestsWithDefaultRatioAndNoOverride() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null));
        assertThat(resourceCalculator.getCpuLimit(), is("1000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("1024Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("100m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("102.4Mi"));
    }

    @Test
    public void calculateRequestsWithProvidedRatioNoOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null));
        assertThat(resourceCalculator.getCpuLimit(), is("1000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("1024Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("200m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("204.8Mi"));
    }

    @Test
    public void calculateRequestsWithDefaultRatioButWithALimitOverride() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2048Mi")
                        .withCpuLimit("2000m")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getCpuLimit(), is("2000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("200m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("204.8Mi"));
    }

    @Test
    public void calculateRequestsWithProvidedRatioButWithALimitOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2048Mi")
                        .withCpuLimit("2000m")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getCpuLimit(), is("2000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("400m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("409.6Mi"));
    }

    @Test
    public void calculateRequestsWithDefaultRatioButWithRequestAndLimitOverrides() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2048Mi")
                        .withMemoryRequest("256Mi")
                        .withCpuLimit("2000m")
                        .withCpuRequest("50m")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getCpuLimit(), is("2000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("50m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("256Mi"));
    }
}
