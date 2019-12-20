package org.entando.kubernetes.controller.test.support;

import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.model.DbmsImageVendor;
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
                .withDbms(DbmsImageVendor.POSTGRESQL)
                .withStandardServerImage(JeeServer.WILDFLY)
                .withReplicas(2)
                .endSpec()
                .build();
    }
}
