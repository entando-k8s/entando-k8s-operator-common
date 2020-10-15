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
import org.entando.kubernetes.controller.common.ConfigurableStorageCalculator;
import org.entando.kubernetes.controller.common.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.app.EntandoAppSpecBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ConfigurableStorageCalculatorTest {

    @AfterEach
    @BeforeEach
    void resetProvidedRatio() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty());
    }

    @Test
    void calculateRequestsWithDefaultRatioAndNoOverride() {
        ConfigurableStorageCalculator resourceCalculator = new ConfigurableStorageCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null));
        assertThat(resourceCalculator.getStorageLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getStorageRequest(), is("204.8Mi"));
    }

    @Test
    void calculateRequestsWithProvidedRatioNoOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableStorageCalculator resourceCalculator = new ConfigurableStorageCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null));
        assertThat(resourceCalculator.getStorageLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getStorageRequest(), is("409.6Mi"));
    }

    @Test
    void calculateRequestsWithDefaultRatioButWithALimitOverride() {
        ConfigurableStorageCalculator resourceCalculator = new ConfigurableStorageCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withStorageLimit("4096Mi")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getStorageLimit(), is("4096Mi"));
        assertThat(resourceCalculator.getStorageRequest(), is("409.6Mi"));
    }

    @Test
    void calculateRequestsWithProvidedRatioButWithALimitOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableStorageCalculator resourceCalculator = new ConfigurableStorageCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withStorageLimit("4096Mi")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getStorageLimit(), is("4096Mi"));
        assertThat(resourceCalculator.getStorageRequest(), is("819.2Mi"));
    }

    @Test
    void calculateRequestsWithDefaultRatioButWithRequestAndLimitOverrides() {
        ConfigurableStorageCalculator resourceCalculator = new ConfigurableStorageCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withStorageLimit("4096Mi")
                        .withStorageRequest("256Mi")
                        .endResourceRequirements().build()), null));
        assertThat(resourceCalculator.getStorageLimit(), is("4096Mi"));
        assertThat(resourceCalculator.getStorageRequest(), is("256Mi"));
    }
}
