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

package org.entando.kubernetes.test.common;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.SerializingCapabilityProvider;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.command.SerializingDeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SupportedCommand;
import org.entando.kubernetes.controller.spi.common.ConfigProperty;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.AbstractK8SClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.model.common.InternalServerStatus;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

public interface ControllerTestHelper {

    String DEFAULT_TLS_SECRET = "default-tls-secret";
    String MY_APP = "my-app";
    String MY_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("my-namespace");

    SimpleK8SClientDouble getClient();

    ScheduledExecutorService getScheduler();

    default Optional<SimpleKeycloakClient> getKeycloakClient() {
        return java.util.Optional.empty();
    }

    default void runControllerAgainst(EntandoCustomResource forResource, CapabilityRequirement capabilityRequirement)
            throws TimeoutException {
        attachKubernetesResource("Resource Requesting Capability", forResource);
        getClient().entandoResources().createOrPatchEntandoResource(forResource);
        attachKubernetesResource("Capability Requirement", capabilityRequirement);
        final StandardCapability capability = capabilityRequirement.getCapability();
        doAnswer(invocationOnMock -> {
            getScheduler().schedule(() -> runControllerAndUpdateCapabilityStatus(invocationOnMock.getArgument(0)), 200L,
                    TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        }).when(getClient().capabilities()).createAndWaitForCapability(argThat(matchesCapability(capability)), anyInt());
        new SerializingCapabilityProvider(getClient().entandoResources(), new AllureAttachingCommandStream(getClient(),
                getKeycloakClient().orElse(null)))
                .provideCapability(forResource, capabilityRequirement, 60);
    }

    default void runControllerAgainst(EntandoCustomResource entandoCustomResource) {
        attachKubernetesResource("Resource Providing Capability", entandoCustomResource);
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME.getJvmSystemProperty(),
                entandoCustomResource.getMetadata().getName());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE.getJvmSystemProperty(),
                entandoCustomResource.getMetadata().getNamespace());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND.getJvmSystemProperty(), entandoCustomResource.getKind());
        getClient().entandoResources().createOrPatchEntandoResource(entandoCustomResource);
        final SimpleKeycloakClient keycloakClient = this.getKeycloakClient().orElse(null);
        final AllureAttachingCommandStream commandStream = new AllureAttachingCommandStream(getClient(), keycloakClient);
        Runnable controller = createController(new SerializingDeploymentProcessor(getClient().entandoResources(), commandStream));
        controller.run();
    }

    default ArgumentMatcher<ProvidedCapability> matchesCapability(StandardCapability capability) {
        return t -> t != null && t.getSpec().getCapability() == capability;
    }

    private void runControllerAndUpdateCapabilityStatus(ProvidedCapability providedCapability) {
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME.getJvmSystemProperty(),
                providedCapability.getMetadata().getName());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE.getJvmSystemProperty(),
                providedCapability.getMetadata().getNamespace());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND.getJvmSystemProperty(), providedCapability.getKind());
        final SimpleKeycloakClient keycloakClient = this.getKeycloakClient().orElse(null);
        final AllureAttachingCommandStream commandStream = new AllureAttachingCommandStream(getClient(), keycloakClient);
        Runnable controller = createController(new SerializingDeploymentProcessor(getClient().entandoResources(), commandStream));
        controller.run();
    }

    Runnable createController(DeploymentProcessor deploymentProcessor);

    default void attachKubernetesResource(String name, Object resource) {
        try {
            Allure.attachment(name, new ObjectMapper(new YAMLFactory()).writeValueAsString(resource));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    default void attachSpiResource(String name, Object resource) {
        Allure.attachment(name, SerializationHelper.serialize(resource));
    }

    default void attachEnvironmentVariable(ConfigProperty prop, String value) {
        System.setProperty(prop.getJvmSystemProperty(), value);
        Allure.attachment("Environment Variable", prop.name() + "=" + value);
    }

    default ProvidedCapability putInternalServerStatus(ProvidedCapability providedCapability, int port,
            Map<String, String> derivedDeploymentParameters) {
        return putStatus(providedCapability, port, derivedDeploymentParameters, new InternalServerStatus("main"));
    }

    default Answer<Object> withADatabaseCapabilityStatus(DbmsVendor vendor, String databaseNAme) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                Map<String, String> derivedParameters = new HashMap<>();
                derivedParameters.put(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, databaseNAme);
                derivedParameters.put(ProvidedDatabaseCapability.DBMS_VENDOR_PARAMETER, vendor.name().toLowerCase(Locale.ROOT));
                DbmsVendorConfig dbmsVendorConfig = DbmsVendorConfig.valueOf(vendor.name());
                return putInternalServerStatus(invocationOnMock.getArgument(0), dbmsVendorConfig.getDefaultPort(), derivedParameters);
            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    default ProvidedCapability putExternalServerStatus(ProvidedCapability providedCapability, int port,
            Map<String, String> derivedParameters) {
        final ExposedServerStatus status = new ExposedServerStatus(NameUtils.MAIN_QUALIFIER);
        status.setIngressName(getClient().ingresses().createIngress(providedCapability, new IngressBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(providedCapability.getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX)
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withNewHttp()
                .addNewPath()
                .withNewBackend()
                .withServiceName(providedCapability.getMetadata().getName() + "-" + NameUtils.DEFAULT_SERVICE_SUFFIX)
                .withServicePort(new IntOrString(port))
                .endBackend()
                .withPath("/non-existing")
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build()).getMetadata().getName());
        return putStatus(providedCapability, port, derivedParameters, status);
    }

    private ProvidedCapability putStatus(ProvidedCapability providedCapability, int port,
            Map<String, String> derivedDeploymentParameters, AbstractServerStatus status) {
        providedCapability.getStatus().putServerStatus(status);
        status.setServiceName(getClient().services().createOrReplaceService(providedCapability, new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(NameUtils.standardServiceName(providedCapability))
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withPort(port)
                .endPort()
                .endSpec()
                .build()).getMetadata().getName());
        final Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(NameUtils.standardAdminSecretName(providedCapability))
                .endMetadata()
                .addToStringData("username", "jon")
                .addToStringData("password", "password123")
                .build();
        getClient().secrets().createSecretIfAbsent(providedCapability, secret);
        status.setAdminSecretName(secret.getMetadata().getName());
        derivedDeploymentParameters.forEach(status::putDerivedDeploymentParameter);
        getClient().entandoResources().updateStatus(providedCapability, status);
        getClient().entandoResources().updatePhase(providedCapability, EntandoDeploymentPhase.SUCCESSFUL);
        return getClient().entandoResources().reload(providedCapability);
    }

    default SerializedEntandoResource newResourceRequiringCapability() {
        final SerializedEntandoResource entandoResource = new SerializedEntandoResource();
        entandoResource.setMetadata(new ObjectMetaBuilder()
                .withName(MY_APP)
                .withNamespace(MY_NAMESPACE)
                .build());
        final CustomResourceDefinitionContext ctx = new CustomResourceDefinitionContext.Builder()
                .withGroup("entando.org")
                .withKind("EntandoApp")
                .withVersion("v1")
                .build();
        entandoResource.setDefinition(ctx);
        return entandoResource;
    }

    default void attachKubernetesState(AbstractK8SClientDouble client) {
        final Map<String, Map<String, Collection<? extends HasMetadata>>> kubernetesState = getClient().getKubernetesState();
        kubernetesState.forEach((key, value) ->
                Allure.step(key, () -> value.forEach((s, hasMetadata) -> Allure.step(s,
                        () -> hasMetadata.forEach(m -> attachKubernetesResource(m.getMetadata().getName(), m))))));
    }

    default void theDefaultTlsSecretWasCreatedAndConfiguredAsDefault() {
        Allure.step("And a TLS Secret was created and configured as default", () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_TLS_SECRET_NAME, DEFAULT_TLS_SECRET);
            getClient().secrets().overwriteControllerSecret(new SecretBuilder()
                    .withNewMetadata()
                    .withName(DEFAULT_TLS_SECRET)
                    .withNamespace(MY_NAMESPACE)
                    .endMetadata()
                    .withType("kubernetes.io/tls")
                    .addToData("tls.crt", "")
                    .addToData("tls.key", "")
                    .build());
        });
    }

    class AllureAttachingCommandStream extends InProcessCommandStream {

        public AllureAttachingCommandStream(SimpleK8SClient<EntandoResourceClientDouble> client, SimpleKeycloakClient keycloakClient) {
            super(client, keycloakClient);
        }

        @Override
        public String process(SupportedCommand supportedCommand, String data, int timeoutSeconds) throws TimeoutException {
            Allure.attachment("Input Deployable", data);
            final String result = super.process(supportedCommand, data, timeoutSeconds);
            Allure.attachment("Output Result", result);
            return result;
        }
    }

}
