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

package org.entando.kubernetes.controller.test.support;

import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.JeeServer;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;

public interface TestFixtureFactory {

    String TEST_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("test-namespace");
    String TEST_APP_NAME = EntandoOperatorTestConfig.calculateName("test-app");

    default EntandoApp newEntandoApp() {
        return new EntandoAppBuilder()
                .withNewMetadata()
                .withNamespace(TEST_NAMESPACE)
                .withName(TEST_APP_NAME)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.POSTGRESQL)
                .withStandardServerImage(JeeServer.WILDFLY)
                .withReplicas(2)
                .endSpec()
                .build();
    }
}
