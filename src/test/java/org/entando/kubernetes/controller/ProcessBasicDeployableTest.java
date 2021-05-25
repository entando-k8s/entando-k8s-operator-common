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
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.entando.kubernetes.BasicController;
import org.entando.kubernetes.BasicDeploymentSpec;
import org.entando.kubernetes.controller.ProcessBasicDeployableTest.BasicDeployable.BasicNestedDeployableContainerFluent;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.creators.DeploymentCreator;
import org.entando.kubernetes.fluentspi.DeployableFluent;
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

    private TestResource entandoCustomResource;

    public static class BasicDeployable extends DeployableFluent<BasicDeployable> {

        public class BasicNestedDeployableContainerFluent extends NestedDeployableContainerFluent<BasicNestedDeployableContainerFluent> {

        }

        @Override
        @SuppressWarnings("unchecked")
        public BasicNestedDeployableContainerFluent withNewContainer() {
            return new BasicNestedDeployableContainerFluent();
        }
    }

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
    @AllureId("test11")
    @Description("Should deploy successfully even if only the image and port are specified")
    void absoluteMinimalDeployment() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
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
            runControllerAgainst(entandoCustomResource);
        });
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                    step("and it has a single container with a name reflecting the qualifier 'server'", () -> {
                        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().size()).isEqualTo(1);
                        assertThat(thePrimaryContainerOn(deployment).getName()).isEqualTo("server-container");
                    });
                    step("and this container exports port 8081 with a name that reflects the qualifier 'server'", () ->
                            assertThat(thePortNamed("server-port").on(thePrimaryContainerOn(deployment)).getContainerPort())
                                    .isEqualTo(8081));
                    step("and the image of this container is the previously specified image test/my-image:6.3.2 but with the default "
                                    + "registry "
                                    + "'docker.io' specified",
                            () -> assertThat(thePrimaryContainerOn(deployment).getImage()).isEqualTo("docker.io/test/my-image:6.3.2"));
                    step("And the default resource limits of 256Mi of Memory and 0.5 CPU were specified", () -> {
                        assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("memory").toString())
                                .isEqualTo("256Mi");
                        assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("cpu").toString()).isEqualTo("500m");
                    });
                    step("And all the startupProbe, readinessProbe and livenessProve all verify that port 8081 is receiving connections",
                            () -> {
                                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                            });
                    step("And the startupProbe is guaranteed to allow the maximum boot time required by the container", () -> {
                        final Probe startupProbe = thePrimaryContainerOn(deployment).getStartupProbe();
                        assertThat(startupProbe.getPeriodSeconds() * startupProbe.getFailureThreshold())
                                .isBetween(DeploymentCreator.DEFAULT_STARTUP_TIME,
                                        (int) Math.round(DeploymentCreator.DEFAULT_STARTUP_TIME * 1.1));

                    });
                });
        attachKubernetesState(client);
    }

    @Test
    @AllureId("test11")
    @Description("Should deploy successfully even if only the image and port are specified")
    void absoluteMinimalDeploymentWithoutResourceLimits() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withNewContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                            .withPrimaryPort(8081).done();
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        step("But I have switched off the limits for environments where resource use is not a concern", () -> System.setProperty(
                EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty(), "false"));
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            runControllerAgainst(entandoCustomResource);
        });
        final Deployment deployment = getClient().deployments()
                .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                });
        step("But the default resource limits were left empty", () -> {
            assertThat(thePrimaryContainerOn(deployment).getResources().getLimits()).doesNotContainKey("memory");
            assertThat(thePrimaryContainerOn(deployment).getResources().getLimits()).doesNotContainKey("cpu");
        });
        attachKubernetesState(client);
    }

    @BeforeEach
    void beforeEach() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty());
        step("Given I have registered a CustomResourceDefinition for the resource kind 'TestResource'", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("testrources.test.org.crd.yaml");

        });
    }

    @Test
    @AllureId("test12")
    @Description("Should reflect both direct custom environment variables and environment variables referring to Secret keys")
    void minimalDeploymentWithEnvironmentVariables() {
        step("Given I have a basic Deployable that specifies a Secret to be created", () -> {
            this.deployable = new BasicDeployable();
            attachSpiResource("Deployable", deployable);
        });
        step("And I have a custom resource of kind TestResource with name", () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
        });
        step("And I have created a Secret named 'my-secret' with the key 'my.secret.key' in the same namespace as the "
                        + "TestResource",
                () -> {
                    getClient().secrets().createSecretIfAbsent(entandoCustomResource, new SecretBuilder().withNewMetadata()
                            .withName("my-secret")
                            .endMetadata().addToStringData("my.secret.key", "my.value").build());
                    attachKubernetesResource("Secret", getClient().secrets().loadSecret(entandoCustomResource, "my-secret"));
                });
        step("And I have created a ConfigMap named 'my-config' with the key 'my.config.key' in the same namespace as the "
                        + "TestResource",
                () -> {
                    getClient().secrets().createConfigMapIfAbsent(entandoCustomResource, new ConfigMapBuilder().withNewMetadata()
                            .withName("my-config")
                            .endMetadata().addToData("my.config.key", "my.value").build());
                    attachKubernetesResource("Secret", getClient().secrets().loadSecret(entandoCustomResource, "my-secret"));
                });
        final BasicNestedDeployableContainerFluent container = deployable.withNewContainer().withDockerImageInfo("test/my-image:6.3.2")
                .withPrimaryPort(8081)
                .withNameQualifier("server");
        step("and a basic DeployableContainer with the some custom environment variables set:", () -> {
            step("the environment variable MY_VAR=my-val",
                    () -> container.withEnvVar("MY_VAR", "my-val")
            );
            step("environment variable reference MY_SECRET_VAR from the Secret named 'my-secret' using the key 'my.secret.key'",
                    () -> container.withEnvVarFromSecret("MY_SECRET_VAR", "my-secret", "my.secret.key")
            );
            step("and environment variable reference MY_CONFIG_VAR from the Secret named 'my-config' using the key 'my.config.key'",
                    () -> container.withEnvVarFromConfigMap("MY_CONFIG_VAR", "my-config", "my.config.key")
            );
            container.done();
            attachSpiResource("Container", container);
        });
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
                                    .matches(theSecretKey("my-secret", "my.secret.key")));
                    step("and the variable 'MY_CONFIG_VAR' refers to the key 'my.config.key' on the Secret 'my-config'", () ->
                            assertThat(theVariableReferenceNamed("MY_CONFIG_VAR").on(thePrimaryContainerOn(deployment)))
                                    .matches(theConfigMapKey("my-config", "my.config.key")));
                });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, client, getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));

    }
}
