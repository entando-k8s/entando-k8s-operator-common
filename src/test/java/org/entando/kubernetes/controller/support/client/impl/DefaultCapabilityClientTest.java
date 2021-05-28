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

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.impl.DefaultKubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.test.common.CapabilityStatusEmulator;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@Feature("As a support developer, I would like perform common operations on the ProvidedCapability resources through a simple "
        + "interface to reduce the learning curve")
@EnableRuleMigrationSupport
class DefaultCapabilityClientTest extends AbstractK8SIntegrationTest implements CapabilityStatusEmulator<DefaultSimpleK8SClient> {

    public static final String MY_APP_NAMESPACE_2 = MY_APP_NAMESPACE + "2";
    private DefaultSimpleK8SClient client;

    @BeforeEach
    void deleteAll() throws IOException {
        super.prepareCrdNameMap();
        deleteAll(getFabric8Client().apps().deployments());
        deleteAll(getFabric8Client().services());
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability"))).delete();
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability1"))).delete();
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability2"))).delete();
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE_2).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability"))).delete();
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE_2).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability1"))).delete();
        getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE_2).withName(NameUtils.standardAdminSecretName(
                newCapability("my-capability2"))).delete();
        deleteAll(getFabric8Client().extensions().ingresses());
        deleteAll(getFabric8Client().pods());
        deleteAll(getFabric8Client().customResources(ProvidedCapability.class));
        deleteAll(getFabric8Client().v1().events());
    }

    public DefaultSimpleK8SClient getClient() {
        this.client = Objects.requireNonNullElseGet(this.client, () -> new DefaultSimpleK8SClient(getFabric8Client()));
        return this.client;
    }

    @Test
    @Description("Should resolve a ProvidedCapability at  Cluster scope by labels")
    void shouldResolveFromClusterByLabels() {
        step(format("Given I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE), () -> attachResource("ProvidedCapability 1", getClient().entandoResources()
                .createOrPatchEntandoResource(new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE)
                        .withName("my-capability1")
                        .addToLabels("my-label", "value1")
                        .endMetadata()
                        .withNewSpec()
                        .withCapability(StandardCapability.SSO).withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                        .endSpec().build())));
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2), () -> attachResource("ProvidedCapability 1", getClient().entandoResources()
                .createOrPatchEntandoResource(
                        new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE_2)
                                .withName("my-capability2")
                                .addToLabels("my-label", "value2")
                                .endMetadata()
                                .withNewSpec()
                                .withCapability(StandardCapability.SSO)
                                .withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                                .endSpec().build())));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE);
        });
        step("Expect ProvidedCapability 'my-capability2' to be resolved using the label 'my-label=value2'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value2")).get();
            attachResource("Resolve ProvidedCapability 'my-capability2'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability2");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_2);
        });
    }

    @Test
    @Description("Should resolve a ProvidedCapability at  Namespace scope by labels")
    void shouldResolveFromNamespaceByLabels() {
        step(format("Given I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE), () -> attachResource("ProvidedCapability 1", getClient().entandoResources()
                .createOrPatchEntandoResource(new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE)
                        .withName("my-capability1")
                        .addToLabels("my-label", "value1")
                        .endMetadata()
                        .withNewSpec()
                        .withCapability(StandardCapability.SSO).withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                        .endSpec().build())));
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2), () -> attachResource("ProvidedCapability 1", getClient().entandoResources()
                .createOrPatchEntandoResource(
                        new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE_2)
                                .withName("my-capability2")
                                .addToLabels("my-label", "value2")
                                .endMetadata()
                                .withNewSpec()
                                .withCapability(StandardCapability.SSO)
                                .withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                                .endSpec().build())));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities()
                    .providedCapabilityByLabels(MY_APP_NAMESPACE, Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE);
        });
        step("Expect ProvidedCapability 'my-capability2' to be resolved using the label 'my-label=value2'", () -> {
            assertThat(getClient().capabilities().providedCapabilityByLabels(MY_APP_NAMESPACE, Map.of("my-label", "value2"))).isEmpty();
        });
    }

    @Test
    @Description("Should resolve a providedCapability by name and namespace")
    void shouldResolveByNameAndNamespace() {
        step("Given I have created a ProvidedCapability", () -> {
            attachResource("ProvidedCapability", getClient().entandoResources()
                    .createOrPatchEntandoResource(newCapability("my-capability")));
        });
        step("Expect it to be resolved by name and namespace", () -> {
            final Optional<ProvidedCapability> actualCapability = getClient().capabilities()
                    .providedCapabilityByName(MY_APP_NAMESPACE, "my-capability");
            assertThat(actualCapability).isPresent();
            attachResource("Resolved ProvidedCapability", actualCapability.get());
        });
    }

    private ProvidedCapability newCapability(String name) {
        return new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE)
                .withName(name).endMetadata()
                .withNewSpec()
                .withCapability(StandardCapability.SSO).withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                .endSpec().build();
    }

    @Test
    @Description("Should resolve a providedCapability by name and namespace")
    void shouldBuildACapabilityResult() {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have created a ProvidedCapability", () -> {
            final ProvidedCapability providedCapability = getClient().entandoResources()
                    .createOrPatchEntandoResource(newCapability("my-capability"));
            capability.set(providedCapability);
            attachResource("ProvidedCapability", providedCapability);
        });
        step("And I have updated its status with an ExternalServerStatus", () -> {
            putExternalServerStatus(capability.get(), "myhost.com", 8081, "/my-context", Collections.emptyMap());
        });
        ValueHolder<CapabilityProvisioningResult> result = new ValueHolder<>();
        step("When I build its CapabilityProvisioningResult", () -> {
            result.set(getClient().capabilities().buildCapabilityProvisioningResult(capability.get()));
        });
        step("Then its Service resolved successfully", () -> {
            assertThat(result.get().getService()).isNotNull();
            attachResource("Service", result.get().getService());
        });
        step("And its admin Secret resolved successfully", () -> {
            assertThat(result.get().getAdminSecret()).isNotNull();
            attachResource("Admin Secret", result.get().getAdminSecret().get());
        });
        step("Then its Ingress resolved successfully", () -> {
            assertThat(result.get().getIngress()).isNotNull();
            attachResource("Ingress", result.get().getIngress().get());
        });
    }

    @Test
    @Description("Should create a providedCapability and wait for its status to enter a complectionPhase")
    void shouldCreateAProvidedCapabilityAndWaitForItsStatusToEnterAComplectionPhase() {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have a ProvidedCapability", () -> {
            capability.set(newCapability("my-capability"));
            attachResource("ProvidedCapability", capability.get());
        });
        step("And there is a background process such as a controller that updates the Phase on its Status to 'SUCCESSFUL'", () -> {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(() -> {
                try {
                    DefaultKubernetesClientForControllers clientForControllers = new DefaultKubernetesClientForControllers(
                            getFabric8Client());
                    await().atMost(10, TimeUnit.SECONDS)
                            .until(() -> clientForControllers.load(ProvidedCapability.class, MY_APP_NAMESPACE, "my-capability") != null);
                    capability.set(clientForControllers.load(ProvidedCapability.class, MY_APP_NAMESPACE, "my-capability"));
                    capability.set(clientForControllers.updateStatus(capability.get(), new ExposedServerStatus("server")));
                    capability.set(clientForControllers.updatePhase(capability.get(), EntandoDeploymentPhase.SUCCESSFUL));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 2, TimeUnit.SECONDS);
        });
        step("When I create the capability and wait for its completion phase", () -> {
            capability.set(getClient().capabilities().createAndWaitForCapability(
                    capability.get(), 10));
            attachResource("ProvidedCapability", capability.get());
        });
        step("When it reflects the 'SUCCESSFUL' Phase and the correct state", () -> {
            assertThat(capability.get().getMetadata().getName()).isEqualTo("my-capability");
            assertThat(capability.get().getSpec().getCapability()).isEqualTo(StandardCapability.SSO);
            assertThat(capability.get().getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.SUCCESSFUL);

            attachResource("ProvidedCapability", capability.get());
        });
    }

    @Test
    @Description("Should throw a TimeoutException when a providedCapability does not enter a complectionPhase within the time specified")
    void shouldThrowTimeoutException() throws TimeoutException {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have a ProvidedCapability", () -> {
            capability.set(newCapability("my-capability"));
            attachResource("ProvidedCapability", capability.get());
        });
        step("And there is no background process that updates the Phase on its Status");
        ValueHolder<ThrowableAssert> exceptionAssert = new ValueHolder<>();
        step("When I create the capability and wait for its completion phase with a timeout of one second", () -> {
            exceptionAssert.set((ThrowableAssert) assertThatThrownBy(() ->
                    getClient().capabilities().createAndWaitForCapability(
                            capability.get(), 1)));
        });
        step("Then a TimeoutException is through", () -> {
            exceptionAssert.get().isInstanceOf(TimeoutException.class);
        });
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE, MY_APP_NAMESPACE_2};
    }
}
