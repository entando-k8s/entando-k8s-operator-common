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

package org.entando.kubernetes.test.e2etest.helpers;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.support.client.impl.DefaultIngressClient;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.FluentIntegrationTesting;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixtureRequest;
import org.entando.kubernetes.controller.support.creators.IngressCreator;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;
import org.entando.kubernetes.model.common.KeycloakAwareSpec;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.e2etest.ControllerExecutor;
import org.entando.kubernetes.test.e2etest.common.ControllerContainerStartingListener;
import org.entando.kubernetes.test.e2etest.common.ControllerStartupEventFiringListener;
import org.entando.kubernetes.test.e2etest.common.ControllerStartupEventFiringListener.OnStartupMethod;
import org.entando.kubernetes.test.e2etest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.test.e2etest.podwaiters.ServicePodWaiter;

public class E2ETestHelperBase<R extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>
        implements FluentIntegrationTesting, CommonLabels {

    protected final DefaultKubernetesClient client;
    protected final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> operations;
    private final String domainSuffix;
    private final ControllerStartupEventFiringListener<R> startupEventFiringListener;
    private final ControllerContainerStartingListener<R> containerStartingListener;

    protected E2ETestHelperBase(DefaultKubernetesClient client, Class<R> type) {
        this.client = client;
        this.operations = client.customResources(type);
        domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
        containerStartingListener = new ControllerContainerStartingListener<>(this.operations);
        startupEventFiringListener = new ControllerStartupEventFiringListener<>(getOperations());
    }

    protected static void logWarning(String x) {
        System.out.println(x);
    }

    public void afterTest() {
        startupEventFiringListener.stopListening();
        containerStartingListener.stopListening();
    }

    public MixedOperation<R, KubernetesResourceList<R>, Resource<R>> getOperations() {
        return operations;
    }

    public void releaseAllFinalizers(String namespace) {
        List<R> resList = this.getOperations().inNamespace(namespace).list().getItems();
        for (R r : resList) {
            r.getMetadata().setFinalizers(Collections.emptyList());
            this.getOperations()
                    .inNamespace(namespace)
                    .withName(r.getMetadata().getName())
                    .patch(r);
        }

    }

    public void setTestFixture(TestFixtureRequest request) {
        TestFixturePreparation.prepareTestFixture(this.client, request);
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

    public <S extends EntandoDeploymentSpec> JobPodWaiter waitForDbJobPod(JobPodWaiter mutex,
            EntandoBaseCustomResource<S, EntandoCustomResourceStatus> resource,
            String deploymentQualifier) {
        await().atMost(45, TimeUnit.SECONDS).ignoreExceptions().until(
                () -> client.pods().inNamespace(resource.getMetadata().getNamespace())
                        .withLabels(dbPreparationJobLabels(resource, deploymentQualifier)).list().getItems()
                        .size() > 0);
        Pod pod = client.pods().inNamespace(resource.getMetadata().getNamespace())
                .withLabels(dbPreparationJobLabels(resource, deploymentQualifier)).list().getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(resource.getMetadata().getNamespace()).withName(pod.getMetadata().getName()));
        return mutex;
    }

    public ServicePodWaiter waitForServicePod(ServicePodWaiter mutex, String namespace, String deploymentName) {
        await().atMost(45, TimeUnit.SECONDS).ignoreExceptions().until(
                () -> client.pods().inNamespace(namespace).withLabel(LabelNames.DEPLOYMENT.getName(), deploymentName).list()
                        .getItems().size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(LabelNames.DEPLOYMENT.getName(), deploymentName).list()
                .getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    public void listenAndRespondWithStartupEvent(String namespace, OnStartupMethod onStartupMethod) {
        startupEventFiringListener.listen(namespace, onStartupMethod);
    }

    public void listenAndRun(String namespace, Runnable runnable) {
        startupEventFiringListener.listen(namespace, runnable);
    }

    public void listenAndRespondWithPod(String namespace, Optional<String> imageVersion) {
        String versionToUse = imageVersion.orElse(EntandoOperatorTestConfig.getVersionOfImageUnderTest().orElse("6.0.0-dev"));
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, versionToUse);
    }

    public void listenAndRespondWithImageVersionUnderTest(String namespace) {
        String versionToUse = EntandoOperatorTestConfig.getVersionOfImageUnderTest().orElseThrow(() -> new IllegalStateException(
                "The property 'entando.test.image.version' has not been set. Please set this property in your Maven command line"));
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, versionToUse);
    }

    public void listenAndRespondWithLatestImage(String namespace) {
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, null);
    }

    public String determineRealm(KeycloakAwareSpec spec) {
        return KeycloakName.ofTheRealm(spec::getKeycloakToUse);
    }

}

