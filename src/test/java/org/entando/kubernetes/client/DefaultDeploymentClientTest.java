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

package org.entando.kubernetes.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.test.common.EntandoOperatorTestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultDeploymentClientTest extends AbstractK8SIntegrationTest {

    private final EntandoApp customResource = newTestEntandoApp();

    @Test
    void shouldReflectChangesThatWerePatchedAfterInitialCreation() {
        Deployment firstDeployment = getSimpleK8SClient().deployments().createOrPatchDeployment(
                customResource, new DeploymentBuilder().withNewMetadata()
                        .withName("my-deployment")
                        .withNamespace(customResource.getMetadata().getNamespace())
                        .endMetadata()
                        .withNewSpec()
                        .withNewSelector()
                        .addToMatchLabels("my-test-label", "bla-bla")
                        .endSelector()
                        .withNewTemplate()
                        .withNewMetadata()
                        .addToLabels("my-test-label", "bla-bla")
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withImage("centos/nginx-116-centos7")
                        .withName("nginx")
                        .withCommand("/usr/libexec/s2i/run")
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build());
        if (EntandoOperatorTestConfig.emulateKubernetes()) {
            firstDeployment = emulateUpscaling(customResource);
        }

        thePrimaryContainerOn(firstDeployment).getEnv().add(new EnvVar("MY_VAR", "myvalue", null));
        if (EntandoOperatorTestConfig.emulateKubernetes()) {
            scheduleDownscalingBehaviour(customResource, 300);
        }

        getSimpleK8SClient().deployments().createOrPatchDeployment(customResource, firstDeployment);
        final Deployment secondDeployment = getSimpleK8SClient().deployments().loadDeployment(customResource, "my-deployment");
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(secondDeployment)), is("myvalue"));

    }

    private void scheduleDownscalingBehaviour(EntandoApp customResource, long incrementSize) {
        //scale down (NB! resource.updateStatus doesn't work on the K8S Mock Server)
        scheduler.schedule(() -> scaleDeployment(customResource, 0, "my-deployment"),
                incrementSize,
                TimeUnit.MILLISECONDS);
        //delete pods
        scheduler.schedule((Runnable) server.getClient().pods().inNamespace(customResource.getMetadata().getNamespace())::delete,
                incrementSize * 2,
                TimeUnit.MILLISECONDS);
        //notify PodWaiter
        scheduler.schedule(() -> takePodWatcherFrom(getSimpleK8SClient().deployments()).eventReceived(Action.ADDED, null),
                incrementSize * 3,
                TimeUnit.MILLISECONDS);
    }

    private Deployment scaleDeployment(EntandoApp customResource, int replicas, String name) {
        final RollableScalableResource<Deployment> operation = server
                .getClient().apps().deployments().inNamespace(customResource.getMetadata().getNamespace()).withName(name);
        operation.updateStatus(
                new DeploymentBuilder(operation.fromServer().get()).withNewStatus().withReplicas(replicas).withObservedGeneration(100L)
                        .endStatus().build());
        return operation.fromServer().get();
    }

    private Deployment emulateUpscaling(EntandoApp customResource) {
        //Scale up (NB! resource.updateStatus doesn't work on the K8S Mock Server)
        Deployment d = scaleDeployment(customResource, 1, "my-deployment");
        getSimpleK8SClient().pods().start(podFrom(d));
        return d;
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE};
    }
}
