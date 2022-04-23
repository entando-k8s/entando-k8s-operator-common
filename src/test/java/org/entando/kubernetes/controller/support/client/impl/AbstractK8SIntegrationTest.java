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

package org.entando.kubernetes.controller.support.client.impl;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.qameta.allure.Allure;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.InterProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractK8SIntegrationTest implements FluentTraversals {

    public static final String MY_APP_NAMESPACE_1 = EntandoOperatorTestConfig.calculateNameSpace(
            InterProcessTestData.MY_APP_DEFAULT_NAMESPACE);
    public static final String MY_APP_NAMESPACE_2 = companionResourceOf(MY_APP_NAMESPACE_1);
    public static final String TEST_CONTROLLER_POD = "test-controller-pod";
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    protected KubernetesClient fabric8Client;

    protected static long TIMEOUT_HUNDREDS_MULTIPLIER = EntandoOperatorTestConfig.getTimeoutHundredsMultiplier();

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public static java.time.Duration mkTimeout(long baseSeconds) {
        return java.time.Duration.ofSeconds(mkTimeoutSec(baseSeconds));
    }

    public static long mkTimeoutSec(long baseSeconds) {
        return baseSeconds * TIMEOUT_HUNDREDS_MULTIPLIER / 100;
    }

    protected void awaitDefaultToken(String namespace) {
        await().atMost(mkTimeout(60)).ignoreExceptions()
                .until(() -> getFabric8Client().secrets().inNamespace(namespace).list()
                        .getItems().stream()
                        .anyMatch(secret -> TestFixturePreparation.isValidTokenSecret(secret, "default")));
    }

    protected TestResource newTestResource() {
        return new TestResource()
                .withNames(MY_APP_NAMESPACE_1, "my-app")
                .withSpec(new BasicDeploymentSpecBuilder()
                        .withReplicas(1)
                        .build());
    }

    public static String companionResourceOf(String namespace) {
        return namespace + "-bis";
    }

    @AfterEach
    void teardown() {
        scheduler.shutdownNow();
        System.clearProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_OF_INTEREST.getJvmSystemProperty());
    }

    protected final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    protected KubernetesClient getFabric8Client() {
        return this.fabric8Client;
    }

    protected void attachResource(String name, HasMetadata resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    protected void attachResources(String name, Collection<? extends HasMetadata> resource)
            throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    @BeforeEach
    public void setup() {
        System.out.println(
                "\n.\n.\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n.\n.\n");
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(),
                TEST_CONTROLLER_POD);
        fabric8Client = new SupportProducer().getKubernetesClient();

        for (String nsToUse : getNamespacesToUse()) {
            await().atMost(mkTimeout(240)).until(() -> {
                Resource<Namespace> nsres = fabric8Client.namespaces().withName(nsToUse);
                try {
                    Namespace ns = nsres.get();
                    if (ns == null) {
                        return true;
                    } else {
                        nsres.delete();
                        return false;
                    }
                } catch (KubernetesClientException ex) {
                    Status status = ex.getStatus();
                    if (status == null) {
                        throw new IllegalStateException("No kubernetes connection profile is active");
                    }
                    if (isStatusFatalClientConnectionError(status)) {
                        throw new IllegalStateException(
                                "Unable to access the test playground (" + ex.getStatus().getCode() + ")");
                    }
                    return false;
                } catch (Exception ex) {
                    return false;
                }
            });
        }

        for (String ns : getNamespacesToUse()) {
            TestFixturePreparation.createNamespace(fabric8Client, ns);
        }
    }

    private boolean isStatusFatalClientConnectionError(Status status) {
        Integer resultCode = status.getCode();
        return resultCode >= 400 && resultCode < 500 && resultCode != 404 && resultCode != 408 && resultCode != 425
                && resultCode != 429;
    }

    protected abstract String[] getNamespacesToUse();

    public ServiceAccount prepareTestServiceAccount(DefaultSimpleK8SClient client, EntandoCustomResource peer,
            String name) {
        ServiceAccountClient serviceAccountClient = client.serviceAccounts();
        final var tmpServiceAccount = serviceAccountClient
                .findOrCreateServiceAccount(peer, name);
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                client.serviceAccounts().findServiceAccount(peer, name) != null
        );
        final var serviceAccount = tmpServiceAccount.done();
        return serviceAccount;
    }
}
