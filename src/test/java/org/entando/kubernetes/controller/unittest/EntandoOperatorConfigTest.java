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

package org.entando.kubernetes.controller.unittest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.entando.kubernetes.controller.EntandoOperatorComplianceMode;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.OperatorDeploymentType;
import org.entando.kubernetes.controller.SecurityMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class EntandoOperatorConfigTest {

    @AfterEach
    void resetPropertiesTested() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty());
    }

    @Test
    void testDeploymentType() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty());
        assertThat(EntandoOperatorConfig.getOperatorDeploymentType(), is(OperatorDeploymentType.HELM));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.OLM.getName());
        assertThat(EntandoOperatorConfig.getOperatorDeploymentType(), is(OperatorDeploymentType.OLM));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.HELM.getName());
        assertThat(EntandoOperatorConfig.getOperatorDeploymentType(), is(OperatorDeploymentType.HELM));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(), "invalid");
        assertThat(EntandoOperatorConfig.getOperatorDeploymentType(), is(OperatorDeploymentType.HELM));
    }

    @Test
    void testComplianceMode() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty());
        assertThat(EntandoOperatorConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(),
                EntandoOperatorComplianceMode.REDHAT.getName());
        assertThat(EntandoOperatorConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.REDHAT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(),
                EntandoOperatorComplianceMode.COMMUNITY.getName());
        assertThat(EntandoOperatorConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(), "invalid");
        assertThat(EntandoOperatorConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
    }

    @Test
    void testSecurityMode() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(),
                SecurityMode.STRICT.getName());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.STRICT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(),
                SecurityMode.LENIENT.getName());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(), "invalid");
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
    }

}
