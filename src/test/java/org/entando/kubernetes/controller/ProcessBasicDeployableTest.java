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

package org.entando.kubernetes.controller;

import static io.qameta.allure.Allure.step;
import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.entando.kubernetes.BasicController;
import org.entando.kubernetes.BasicDeploymentSpec;
import org.entando.kubernetes.controller.BasicDeployable.NestedDeployableContainerFluent;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.test.common.ControllerTestHelper;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("component"), @Tag("in-process"), @Tag("allure")})
@Feature(
        "As a controller developer, I would like to specify how my image gets deployed providing only the configuration that is "
                + "absolutely required so that "
                + " I can focus on my development tasks")
@Issue("ENG-2284")
@SourceLink("ProcessBasicDeployableTest.java")
class ProcessBasicDeployableTest implements ControllerTestHelper, FluentTraversals, VariableReferenceAssertions {

    protected final SimpleK8SClientDouble client = new SimpleK8SClientDouble();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
    private BasicDeployable deployable;

    @Override
    public SimpleK8SClientDouble getClient() {
        return client;
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduledExecutorService;
    }

    @Override
    public Runnable createController(DeploymentProcessor deploymentProcessor) {
        return new BasicController(getClient().entandoResources(), deploymentProcessor).withDeployable(deployable)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @Description("Should deploy successfully even if only the image and port are specified")
    void shouldDeploySuccessfullyWithImageAndPortOnly() {
        step("And I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withNewContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                            .withPrimaryPort(8081).done();
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            attachKubernetesResource("TestResource", entandoCustomResource);
            runControllerAgainst(entandoCustomResource);
        });
        step("Then a Deployment was created", () -> {
            final Deployment deployment = getClient().deployments()
                    .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
            attachKubernetesResource("Deployment", deployment);
            assertThat(deployment).isNotNull();
            step("and it has a single container with a name reflecting the qualifier 'server'", () -> {
                assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().size()).isEqualTo(1);
                assertThat(thePrimaryContainerOn(deployment).getName()).isEqualTo("server-container");
            });
            step("and this container exports port 8081 with a name that reflects the qualifier 'server'", () ->
                    assertThat(thePortNamed("server-port").on(thePrimaryContainerOn(deployment)).getContainerPort()).isEqualTo(8081));
            step("and the image of this container is the previously specified image test/my-image:6.3.2 but with the default registry "
                            + "'docker.io' specified",
                    () ->
                            assertThat(thePrimaryContainerOn(deployment).getImage()).isEqualTo("docker.io/test/my-image:6.3.2"));
            step("And the default resource limits of 256Mi of Memory and 0.5 CPU were specified", () -> {
                assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("memory").toString()).isEqualTo("256Mi");
                assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("cpu").toString()).isEqualTo("500m");
            });
        });
        attachKubernetesState(client);
    }

    @BeforeEach
    void beforeEach() {
        step("Given I have registered a CustomResourceDefinition for the resource kind 'TestResource'", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("testrources.test.org.crd.yaml");

        });
    }

    @Test
    @Description("Should reflect custom both direct environment variables and environment variables referring to Secret keys")
    void shouldReflectCustomEnvironmentVariables() {
        step("And I have a basic Deployable that specifies a Secret to be created", () -> {
            this.deployable = new BasicDeployable();
            step("and the Secret is named 'my-secret' and has a key 'my.key' with the value 'my.value'",
                    () -> deployable.withSecret("my-secret", Map.of("my.key", "my.value"))
            );
            attachSpiResource("Deployable", deployable);
        });
        final NestedDeployableContainerFluent container = deployable.withNewContainer().withDockerImageInfo("test/my-image:6.3.2")
                .withPrimaryPort(8081)
                .withNameQualifier("server");
        step("and a basic DeployableContainer with the some custom environment variables set", () -> {
            step("and a basic DeployableContainer with the environment variable MY_VAR=my-val",
                    () -> container.withEnvVar("MY_VAR", "my-val")
            );
            step("and an environment variable reference MY_SECRET_VAR from the Secret named 'my-secret' using the key 'my.key'",
                    () -> container.withEnvVarFromSecret("MY_SECRET_VAR", "my-secret", "my.key")
            );
            container.done();
            attachSpiResource("Container", container);
        });
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            attachKubernetesResource("TestResource", entandoCustomResource);
            runControllerAgainst(entandoCustomResource);
        });
        step("Then the container on the Deployment that was created reflects both the environment variable and the environment variable "
                        + "reference",
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                    step("the value of MY_VAR is 'my-val'", () ->
                            assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(deployment))).isEqualTo("my-val"));
                    step("and the variable 'MY_SECRET_VAR' refers to the key 'my.key' on the Secret 'my-secret'", () ->
                            assertThat(theVariableReferenceNamed("MY_SECRET_VAR").on(thePrimaryContainerOn(deployment)))
                                    .matches(theSecretKey("my-secret", "my.key")));
                });
        step("And the Secret 'my-secret' was created in the same namespace and contains the key 'my.key' with the value 'my.value'", () -> {
            final Secret secret = getClient().secrets().loadSecret(entandoCustomResource, "my-secret");
            assertThat(secret.getStringData())
                    .containsEntry("my.key", "my.value");
            attachKubernetesResource("Secret", secret);
        });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, client, getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));

    }
}
