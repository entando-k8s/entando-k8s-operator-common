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
import com.google.common.cache.AbstractCache.StatsCounter;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.qameta.allure.Allure;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractK8SIntegrationTest implements FluentTraversals {

    public static final String MY_APP_NAMESPACE_1 = EntandoOperatorTestConfig.calculateNameSpace("my-app-namespace") + "-test66";
    public static final String MY_APP_NAMESPACE_2 = MY_APP_NAMESPACE_1 + "2";
    public static final String TEST_CONTROLLER_POD = "test-controller-pod";
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    protected KubernetesClient fabric8Client;

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    protected void awaitDefaultToken(String namespace) {
        await().atMost(30, TimeUnit.SECONDS).ignoreExceptions()
                .until(() -> getFabric8Client().secrets().inNamespace(namespace).list()
                        .getItems().stream().anyMatch(secret -> TestFixturePreparation.isValidTokenSecret(secret, "default")));
    }

    protected TestResource newTestResource() {
        return new TestResource()
                .withNames(MY_APP_NAMESPACE_1, "my-app")
                .withSpec(new BasicDeploymentSpecBuilder()
                        .withReplicas(1)
                        .build());
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

    protected void attachResources(String name, Collection<? extends HasMetadata> resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    @BeforeEach
    public void setup() {
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(), TEST_CONTROLLER_POD);
        fabric8Client = new SupportProducer().getKubernetesClient();
        for (String ns : getNamespacesToUse()) {
            System.out.println("> Setting up the namespace " + ns);
            await().atMost(240, TimeUnit.SECONDS).ignoreExceptions()
                    .until(() -> {
                        if (fabric8Client.namespaces().withName(ns).get() == null) {
                            return true;
                        } else {
                            fabric8Client.namespaces().withName(ns).delete();
                            return false;
                        }
                    });
        }

        for (String ns : getNamespacesToUse()) {
            System.out.println(String.format("> Creating and populating the test namespace %s", ns));
            TestFixturePreparation.createNamespace(fabric8Client, ns);
            destroyNamespaceUndeads(fabric8Client, ns);
        }
        System.out.println("> Setup completed");
    }

    /**
     * Destroys undead resources generated by bugs. Undead resources are namespace resources that survives the deletion of their namespace
     * due to bugs on the kube implementation.
     */
    void destroyNamespaceUndeads(KubernetesClient fabric8Client, String namespace) {
        // INGRESSES CLEANUP
        var numberOfDeleted = new Object() {
            int n = -1;
        };
        while (numberOfDeleted.n != 0) {
            try {
                Thread.sleep((numberOfDeleted.n == -1) ? 0 : 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            numberOfDeleted.n = 0;
            fabric8Client.extensions().ingresses().inNamespace(namespace).list().getItems().forEach(e -> {
                System.out.println(String.format("Deleting undead ingress %s", e.getMetadata().getName()));
                fabric8Client.extensions().ingresses().inNamespace(namespace).withName(e.getMetadata().getName()).delete();
                numberOfDeleted.n++;
            });
            // DEPLOYMENTSs CLEANUP
            fabric8Client.apps().deployments().inNamespace(namespace).list().getItems().forEach(e -> {
                System.out.println(String.format("Deleting undead deployment %s", e.getMetadata().getName()));
                fabric8Client.apps().deployments().inNamespace(namespace).withName(e.getMetadata().getName()).delete();
                numberOfDeleted.n++;
            });
            // PODs CLEANUP
            fabric8Client.pods().inNamespace(namespace).list().getItems().forEach(e -> {
                System.out.println(String.format("Deleting undead pod %s", e.getMetadata().getName()));
                fabric8Client.pods().inNamespace(namespace).withName(e.getMetadata().getName()).delete();
                numberOfDeleted.n++;
            });
            // MISC CLEANUP
            NonNamespaceOperation<ProvidedCapability, KubernetesResourceList<ProvidedCapability>, Resource<ProvidedCapability>> cap =
                    fabric8Client.customResources(ProvidedCapability.class).inNamespace(namespace);
            cap.list().getItems().forEach(e -> {
                System.out.println(String.format("Deleting undead capability %s", e.getMetadata().getName()));
                cap.withName(e.getMetadata().getName()).delete();
                numberOfDeleted.n++;
            });
        }
    }

    private void destroyNamespaceUndeadsByType(String namespace, MixedOperation<Ingress, IngressList, Resource<Ingress>> res) {
        res.inNamespace(namespace).list().getItems()
                .forEach(e -> res.inNamespace(namespace).withName(e.getMetadata().getName()).delete());
    }

    protected abstract String[] getNamespacesToUse();
}
