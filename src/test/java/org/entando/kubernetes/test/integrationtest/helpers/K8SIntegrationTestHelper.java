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

package org.entando.kubernetes.test.integrationtest.helpers;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.controller.support.creators.IngressCreator;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.test.integrationtest.common.EntandoOperatorTestConfig;
import org.entando.kubernetes.test.integrationtest.common.EntandoOperatorTestConfig.TestTarget;
import org.entando.kubernetes.test.integrationtest.common.FluentIntegrationTesting;
import org.entando.kubernetes.test.integrationtest.common.TestFixturePreparation;
import org.entando.kubernetes.test.integrationtest.common.TestFixtureRequest;

public class K8SIntegrationTestHelper implements FluentIntegrationTesting {

    private final DefaultKubernetesClient client = TestFixturePreparation.newClient();
    private final String domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
    private final EntandoPluginIntegrationTestHelper entandoPluginIntegrationTestHelper = new EntandoPluginIntegrationTestHelper(client);
    private final KeycloakIntegrationTestHelper keycloakHelper = new KeycloakIntegrationTestHelper(client);
    private final EntandoAppIntegrationTestHelper entandoAppHelper = new EntandoAppIntegrationTestHelper(client);
    private final ExternalDatabaseIntegrationTestHelper externalDatabaseHelper = new ExternalDatabaseIntegrationTestHelper(client);

    private static void stopStaleWatchersFromFillingUpTheLogs() {
        Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String name = loggerNames.nextElement();
            if (name.contains("WatchConnectionManager")) {
                System.out.println("Reducing logger: " + name);
                Optional.ofNullable(LogManager.getLogManager().getLogger(name))
                        .ifPresent(logger -> logger.setLevel(Level.SEVERE));
            }
        }
    }

    public ExternalDatabaseIntegrationTestHelper externalDatabases() {
        return externalDatabaseHelper;
    }

    public KeycloakIntegrationTestHelper keycloak() {
        return keycloakHelper;
    }

    public EntandoPluginIntegrationTestHelper entandoPlugins() {
        return entandoPluginIntegrationTestHelper;
    }

    public EntandoAppIntegrationTestHelper entandoApps() {
        return this.entandoAppHelper;
    }

    public void afterTest() {
        keycloak().afterTest();
        externalDatabases().afterTest();
        entandoPlugins().afterTest();
        entandoApps().afterTest();
        if (EntandoOperatorTestConfig.getTestTarget() == TestTarget.STANDALONE) {
            client.close();
            stopStaleWatchersFromFillingUpTheLogs();
        }
    }

    public DefaultKubernetesClient getClient() {
        return client;
    }

    public void setTextFixture(TestFixtureRequest request) {
        this.releaseFinalizers(request);
        TestFixturePreparation.prepareTestFixture(this.client, request);
    }

    public void releaseFinalizers(TestFixtureRequest request) {
        for (Entry<String, List<Class<? extends EntandoBaseCustomResource<?>>>> entry : request.getRequiredDeletions().entrySet()) {
            if (client.namespaces().withName(entry.getKey()).get() != null) {
                for (Class<? extends EntandoBaseCustomResource<?>> type : entry.getValue()) {
                    if (type.equals(EntandoKeycloakServer.class)) {
                        this.keycloak().releaseAllFinalizers(entry.getKey());
                    } else if (type.equals(EntandoApp.class)) {
                        this.entandoApps().releaseAllFinalizers(entry.getKey());
                    } else if (type.equals(EntandoPlugin.class)) {
                        this.entandoPlugins().releaseAllFinalizers(entry.getKey());
                    } else if (type.equals(EntandoDatabaseService.class)) {
                        this.externalDatabases().releaseAllFinalizers(entry.getKey());
                    }
                }
            }
        }
    }

    public void releaseAllFinalizers() {
        keycloak().releaseAllFinalizers(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE);
        entandoApps().releaseAllFinalizers(EntandoAppIntegrationTestHelper.TEST_NAMESPACE);
        entandoPlugins().releaseAllFinalizers(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE);
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

}