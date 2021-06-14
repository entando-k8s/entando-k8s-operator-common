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

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.ConfigProperty;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.ExceptionUtils;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.model.common.InternalServerStatus;
import org.entando.kubernetes.model.common.ResourceReference;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

public interface CapabilityStatusEmulator<T extends SimpleK8SClient<? extends EntandoResourceClient>> {

    T getClient();

    ScheduledExecutorService getScheduler();

    default void attachKubernetesResource(String name, Object resource) {
        try {
            Allure.attachment(name, new ObjectMapper(new YAMLFactory()).writeValueAsString(resource));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    default ArgumentMatcher<ProvidedCapability> matchesCapability(StandardCapability capability) {
        return t -> t != null && t.getSpec().getCapability() == capability;
    }

    default <T extends HasMetadata> ArgumentMatcher<T> matchesResource(String namespace, String name) {
        return t -> t != null && namespace.equals(t.getMetadata().getNamespace()) && name.equals(t.getMetadata().getName());
    }

    default ProvidedCapability putInternalServerStatus(ProvidedCapability providedCapability, int port,
            Map<String, String> derivedDeploymentParameters) {
        return putStatus(providedCapability, port, derivedDeploymentParameters, new InternalServerStatus(NameUtils.MAIN_QUALIFIER));
    }

    default ProvidedCapability putExternalServerStatus(ProvidedCapability providedCapability, String host, int port, String path,
            Map<String, String> derivedParameters) {
        final ExposedServerStatus status = new ExposedServerStatus(NameUtils.MAIN_QUALIFIER);
        status.setExternalBaseUrl(format("https://%s%s", host, path));
        final Ingress ingress = getClient().ingresses().createIngress(providedCapability, new IngressBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(NameUtils.standardIngressName(providedCapability))
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost(host)
                .withNewHttp()
                .addNewPath()
                .withNewBackend()
                .withServiceName(NameUtils.standardServiceName(providedCapability))
                .withServicePort(new IntOrString(port))
                .endBackend()
                .withPath(path)
                .endPath()
                .endHttp()
                .endRule()
                .addNewTl()
                .addNewHost(host)

                .endTl()
                .endSpec()
                .build());
        status.setIngressName(ingress.getMetadata().getName());
        final ProvidedCapability updatedCapability = putStatus(providedCapability, port, derivedParameters, status);
        step(format("and the Ingress '%s'", ingress.getMetadata().getName()), () ->
                attachKubernetesResource(providedCapability.getSpec().getCapability().name() + " Ingress", ingress));
        return updatedCapability;
    }

    private ProvidedCapability putStatus(ProvidedCapability providedCapability, int port,
            Map<String, String> derivedDeploymentParameters, AbstractServerStatus status) {
        providedCapability.getStatus().putServerStatus(status);
        status.setProvidedCapability(
                new ResourceReference(providedCapability.getMetadata().getNamespace(), providedCapability.getMetadata().getName()));
        final Service service = getClient().services().createOrReplaceService(providedCapability, new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(NameUtils.standardServiceName(providedCapability))
                .endMetadata()
                .withNewSpec()
                .addNewPort()
                .withPort(port)
                .endPort()
                .endSpec()
                .build());
        status.setServiceName(service.getMetadata().getName());
        step(format("with the %s service '%s'", providedCapability.getSpec().getCapability().name(), service.getMetadata().getName()), () ->
                attachKubernetesResource(providedCapability.getSpec().getCapability().name() + " Service", service));
        final Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(providedCapability.getMetadata().getNamespace())
                .withName(NameUtils.standardAdminSecretName(providedCapability))
                .endMetadata()
                .addToStringData("username", "jon")
                .addToStringData("password", "password123")
                .build();
        getClient().secrets().createSecretIfAbsent(providedCapability, secret);
        step(format("and the admin secret '%s'", secret.getMetadata().getName()), () ->
                attachKubernetesResource("Admin Secret",
                        getClient().secrets().loadSecret(providedCapability, secret.getMetadata().getName())));

        status.setAdminSecretName(secret.getMetadata().getName());
        derivedDeploymentParameters.forEach(status::putDerivedDeploymentParameter);
        getClient().entandoResources().updateStatus(providedCapability, status);
        getClient().entandoResources().updatePhase(providedCapability, EntandoDeploymentPhase.SUCCESSFUL);
        return getClient().entandoResources().reload(providedCapability);
    }

    default void attachSpiResource(String name, Object resource) {
        Allure.attachment(name, SerializationHelper.serialize(resource));
    }

    default void attachEnvironmentVariable(ConfigProperty prop, String value) {
        System.setProperty(prop.getJvmSystemProperty(), value);
        Allure.attachment("Environment Variable", prop.name() + "=" + value);
    }

    default Answer<Object> withADatabaseCapabilityStatus(DbmsVendor vendor, String databaseName) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                Map<String, String> derivedParameters = new HashMap<>();
                derivedParameters.put(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, databaseName);
                derivedParameters.put(ProvidedDatabaseCapability.DBMS_VENDOR_PARAMETER, vendor.name().toLowerCase(Locale.ROOT));
                DbmsVendorConfig dbmsVendorConfig = DbmsVendorConfig.valueOf(vendor.name());
                return putInternalServerStatus(invocationOnMock.getArgument(0), dbmsVendorConfig.getDefaultPort(), derivedParameters);
            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    default Answer<Object> withFailedExposedServerStatus(String qualifier, Exception exception) {
        return withFailedServerStatus(exception, new ExposedServerStatus(qualifier));
    }

    default Answer<Object> withFailedInternalServerStatus(String qualifier, Exception exception) {
        return withFailedServerStatus(exception, new ExposedServerStatus(qualifier));
    }

    private Answer<Object> withFailedServerStatus(Exception exception, ExposedServerStatus exposedServerStatus) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                ProvidedCapability pc = invocationOnMock.getArgument(0);
                exposedServerStatus.finishWith(ExceptionUtils.failureOf(pc, exception));
                pc = getClient().entandoResources().updateStatus(pc, exposedServerStatus);
                return getClient().entandoResources().updatePhase(pc, EntandoDeploymentPhase.FAILED);
            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    default Answer<Object> withAnSsoCapabilityStatus(String host, String realmName) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                Map<String, String> derivedParameters = new HashMap<>();
                derivedParameters.put(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, realmName);
                return putExternalServerStatus(invocationOnMock.getArgument(0), host, 8080, "/auth", derivedParameters);

            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

}
