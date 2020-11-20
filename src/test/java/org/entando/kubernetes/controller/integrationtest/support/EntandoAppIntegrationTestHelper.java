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

package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.app.DoneableEntandoApp;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppList;
import org.entando.kubernetes.model.app.EntandoAppOperationFactory;

public class EntandoAppIntegrationTestHelper extends IntegrationTestHelperBase<EntandoApp, EntandoAppList, DoneableEntandoApp> {

    public static final String TEST_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("test-namespace");
    public static final String TEST_APP_NAME = EntandoOperatorTestConfig.calculateName("test-entando");

    public EntandoAppIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoAppOperationFactory::produceAllEntandoApps);
    }

    public void createAndWaitForApp(EntandoApp entandoApp, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(TEST_NAMESPACE).create(entandoApp);
        if (deployingDbContainers) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    TEST_NAMESPACE, TEST_APP_NAME + "-db");
        }
        this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)),
                TEST_NAMESPACE,
                TEST_APP_NAME + "-server-db-preparation-job");
        //300 because there are 3 containers
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(300 + waitOffset)),
                TEST_NAMESPACE, TEST_APP_NAME + "-server");
        this times out wait for the other pods first
        await().atMost(60, SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(TEST_NAMESPACE)
                            .withName(TEST_APP_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("server").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });

        await().atMost(60, SECONDS).until(() -> HttpTestHelper.read(
                HttpTestHelper.getDefaultProtocol() + "://" + entandoApp.getSpec().getIngressHostName()
                        .orElseThrow(() -> new IllegalStateException())
                        + "/entando-de-app/index.jsp").contains("Entando - Welcome"));
    }

}
