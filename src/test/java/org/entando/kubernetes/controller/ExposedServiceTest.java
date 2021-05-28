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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.nio.file.Paths;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.ExposingControllerFluent;
import org.entando.kubernetes.fluentspi.IngressingContainerFluent;
import org.entando.kubernetes.fluentspi.IngressingDeployableFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.test.common.CertificateSecretHelper;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to expose my service over HTTP so that others can access it")
@Issue("ENG-2284")
@SourceLink("ExposedServiceTest.java")
class ExposedServiceTest extends ControllerTestBase implements VariableReferenceAssertions {

    private BasicIngressingDeployable deployable;
    private TestResource entandoCustomResource;

    public static class BasicExposingController extends ExposingControllerFluent<BasicExposingController> {

        public BasicExposingController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor) {
            super(k8sClient, deploymentProcessor);
        }
    }

    public static class BasicIngressingDeployable extends IngressingDeployableFluent<BasicIngressingDeployable> {

    }

    public static class BasicIngressingContainer extends IngressingContainerFluent<BasicIngressingContainer> {

    }

    @AfterEach
    @BeforeEach
    void resetSystemPropertiesUsed() {
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty());
    }

    @Override
    public Runnable createController(KubernetesClientForControllers kubernetesClientForControllers, DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        return new BasicExposingController(kubernetesClientForControllers, deploymentProcessor)
                .withDeployable(deployable)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @Description("Should expose a service over HTTPS using the host name specified ")
    void exposeHttpsServiceOverSpecifiedHostName() {
        step("Given I have a custom resource of kind TestResource with name 'my-app'", () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
        });
        step("And I have three Kubernetes Secrets",
                () -> {
                    CertificateSecretHelper.buildCertificateSecretsFromDirectory(
                            entandoCustomResource.getMetadata().getNamespace(),
                            Paths.get("src", "test", "resources", "tls", "ampie.dynu.net"))
                            .forEach(secret -> getClient().secrets().createSecretIfAbsent(entandoCustomResource, secret));
                    step(format("a standard TLS Secret named '%s'", CertificateSecretHelper.TEST_TLS_SECRET), () ->
                            attachKubernetesResource("TlsSecret",
                                    getClient().secrets().loadSecret(entandoCustomResource,
                                            CertificateSecretHelper.TEST_TLS_SECRET)));
                    step(format("an Opaque Secret containing trusted certificates '%s' that is also configured as the default CA Secret",
                            CertificateSecretHelper.TEST_CA_SECRET),
                            () -> {
                                attachKubernetesResource("CASecret",
                                        getClient().secrets()
                                                .loadSecret(entandoCustomResource, CertificateSecretHelper.TEST_CA_SECRET));
                                System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty(),
                                        CertificateSecretHelper.TEST_CA_SECRET);
                            });
                    step(format(
                            "another Opaque Secret containing the equivalent Java trust store with the default truststore secret name '%s'",
                            TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET),
                            () -> attachKubernetesResource("TrustStoreSecret",
                                    getClient().secrets()
                                            .loadSecret(entandoCustomResource, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET)));
                });
        step(format(
                "And I have an IngressingDeployable that specifies the TLS Secret %s and the hostname 'myhost.com', targeting an Ingress "
                        + "in the same namespace as the custom resource ",
                CertificateSecretHelper.TEST_TLS_SECRET),
                () -> {
                    this.deployable = new BasicIngressingDeployable()
                            .withIngressHostName("myhost.com")
                            .withTlsSecretName(CertificateSecretHelper.TEST_TLS_SECRET)
                            .withIngressRequired(true)
                            .withIngressNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                            .withIngressName(NameUtils.standardIngressName(entandoCustomResource));
                    attachSpiResource("Deployable", deployable);
                });
        final BasicIngressingContainer container = deployable
                .withContainer(new BasicIngressingContainer().withDockerImageInfo("test/my-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server"));
        step("and a TrustStoreAware, IngressingContainer with the context path '/my-app' and the health check path '/my-app/health'",
                () -> {
                    container.withWebContextPath("/my-app").withHealthCheckPath("/my-app/health");
                    attachSpiResource("Container", container);
                });
        step("When the controller processes a new TestResource", () -> {
            attachKubernetesResource("TestResource", entandoCustomResource);
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step("Then a Deployment was created with a single Container", () -> {
            final Deployment deployment = getClient().deployments()
                    .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
            assertThat(deployment).isNotNull();
            attachKubernetesResource("Deployment", deployment);
            step(format("that has the standard Java truststore variable %s set to point to the "
                            + "Secret key %s.%s", TrustStoreHelper.JAVA_TOOL_OPTIONS, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY),
                    () -> assertThat(theVariableReferenceNamed(TrustStoreHelper.JAVA_TOOL_OPTIONS)
                            .on(thePrimaryContainerOn(deployment)))
                            .matches(theSecretKey(TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY)));
            step("and its startupProbe, readinessProbe and livenessProbe all point to the path /my-app/my-app/health", () -> {
                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
            });
        });
        step("And a Service was created that points to port 8081 on the target Container", () -> {
            final Service service = getClient().services()
                    .loadService(entandoCustomResource, NameUtils.standardServiceName(entandoCustomResource));
            attachKubernetesResource("Service", service);
            assertThat(service).isNotNull();
            assertThat(thePortNamed("server-port").on(service).getPort()).isEqualTo(8081);
            assertThat(thePortNamed("server-port").on(service).getTargetPort().getIntVal()).isEqualTo(8081);
            attachKubernetesResource("Service", service);
        });
        step("And an Ingress for was created", () -> {
            final Ingress ingress = getClient().ingresses()
                    .loadIngress(entandoCustomResource.getMetadata().getNamespace(),
                            NameUtils.standardIngressName(entandoCustomResource));
            attachKubernetesResource("Service", ingress);
            assertThat(ingress).isNotNull();
            step(" that points to port 8081 on the target Service", () -> {
                assertThat(theHttpPath("/my-app").on(ingress).getBackend().getServicePort().getIntVal()).isEqualTo(8081);
                assertThat(theHttpPath("/my-app").on(ingress).getBackend().getServiceName())
                        .isEqualTo(NameUtils.standardServiceName(entandoCustomResource));
            });
            step("and exposes the service on the host 'myhost.com'",
                    () -> assertThat(ingress.getSpec().getRules().get(0).getHost()).isEqualTo("myhost.com"));
            step(format("and uses the TLS Secret '%s' for this host", CertificateSecretHelper.TEST_TLS_SECRET),
                    () -> {
                        assertThat(ingress.getSpec().getTls().get(0).getSecretName()).isEqualTo(CertificateSecretHelper.TEST_TLS_SECRET);
                        assertThat(ingress.getSpec().getTls().get(0).getHosts()).contains("myhost.com");
                    });
        });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, getClient(), getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));

    }

}
