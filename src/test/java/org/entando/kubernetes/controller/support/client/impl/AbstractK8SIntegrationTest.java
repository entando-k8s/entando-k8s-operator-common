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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.impl.DefaultKubernetesClientForControllers;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.DeletionWaiter;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.InterProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractK8SIntegrationTest implements FluentTraversals, InterProcessTestData {

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    protected KubernetesClient fabric8Client;

    protected <R extends HasMetadata,
            L extends KubernetesResourceList<R>,
            O extends Resource<R>> void deleteAll(MixedOperation<R, L, O> operation) {
        for (String s : getNamespacesToUse()) {
            new DeletionWaiter<>(operation).fromNamespace(s).waitingAtMost(100, TimeUnit.SECONDS);
        }
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    protected TestResource newTestResource() {
        return new TestResource()
                .withNames(MY_APP_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpecBuilder()
                        .withReplicas(1)
                        .build());
    }

    @AfterEach
    void teardown() {
        scheduler.shutdownNow();
    }

    protected abstract String[] getNamespacesToUse();

    protected final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    protected KubernetesClient getFabric8Client() {
        return this.fabric8Client;
    }

    protected void prepareCrdNameMap() throws IOException {
        this.fabric8Client.apiextensions().v1beta1().customResourceDefinitions().createOrReplace(objectMapper.readValue(
                Thread.currentThread().getContextClassLoader().getResource("testrources.test.org.crd.yaml"), CustomResourceDefinition.class)
        );
        this.fabric8Client.configMaps().inNamespace(fabric8Client.getNamespace()).createOrReplace(new ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace(fabric8Client.getNamespace())
                .withName(DefaultKubernetesClientForControllers.ENTANDO_CRD_NAMES_CONFIG_MAP)
                .endMetadata()
                .addToData("EntandoApp.entando.org", "entandoapps.entando.org")
                .addToData("EntandoPlugin.entando.org", "entandoplugins.entando.org")
                .addToData("EntandoDatabaseService.entando.org", "entandodatabaseservices.entando.org")
                .addToData("EntandoKeycloakServer.entando.org", "entandokeycloakservers.entando.org")
                .addToData("TestResource.test.org", "testresources.test.org")
                .build());
        new DefaultKubernetesClientForControllers(this.fabric8Client).prepareConfig();
    }

    protected void attachResource(String name, HasMetadata resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    protected void attachResources(String name, Collection<? extends HasMetadata> resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    @BeforeEach
    public void setup() {
        fabric8Client = new DefaultKubernetesClient();
        Arrays.stream(getNamespacesToUse())
                .filter(s -> fabric8Client.namespaces().withName(s).get() == null)
                .forEach(s -> TestFixturePreparation.createNamespace(fabric8Client, s));
    }
}
