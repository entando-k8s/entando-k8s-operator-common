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

package org.entando.kubernetes.controller.spi.capability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.database.DatabaseDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ServiceResult;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
class CapabilityProviderTest implements InProcessTestData {

    @Mock
    SimpleCapabilityClient client;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private ConfigMap foundCapability;
    private static final String OPERATOR_NAMESPACE = "entando-operator";

    @Test
    void shouldProvideClusterScopeCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.CLUSTER, null, null, null);
        when(client.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withServiceResult()));
        when(client.configMapByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.CLUSTER));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(OPERATOR_NAMESPACE));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(OPERATOR_NAMESPACE));
        assertThat(providedCapability.getAdminSecretReference().getName(), is("default-mysql-dbms"));
    }

    private Function<CapabilityRequirement, ServiceResult> withServiceResult() {
        return (capabilityRequirement) -> new DatabaseDeploymentResult(new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName())
                .endMetadata()
                .build(), DbmsDockerVendorStrategy.CENTOS_MYSQL, "my_db",
                capabilityRequirement.getMetadata().getName(), null);
    }

    @Test
    void shouldFailWhenTheWatcherFailed() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.CLUSTER, null, null, null);
        when(client.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        when(client.createAndWatchResource(any(), any())).thenAnswer(andGenerateFailEvent());
        when(client.configMapByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalStateException.class, () -> capabilityProvider.provideCapability(forResource, theRequiredCapability));
    }

    @Test
    void shouldFailWhenThereIsAScopeMismatch() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, Collections.singletonMap("Environment", "Stage"), null,
                null);
        when(client.configMapByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenReturn(Optional.ofNullable(new ConfigMapBuilder().withNewMetadata()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME, CapabilityScope.DEDICATED.getCamelCaseName())
                        .endMetadata().build()));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theRequiredCapability));
    }

    @Test
    void shouldFailWhenThereIsAnImplementationMismatch() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, Collections.singletonMap("Environment", "Stage"), null,
                null);
        when(client.configMapByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenReturn(Optional.ofNullable(new ConfigMapBuilder().withNewMetadata()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME,
                                CapabilityScope.LABELED.getCamelCaseName())
                        .addToLabels(ProvidedCapability.IMPLEMENTATION_LABEL_NAME,
                                StandardCapabilityImplementation.POSTGRESQL.getCamelCaseName())
                        .endMetadata().build()));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theRequiredCapability));
    }

    @Test
    void shouldProvideLabeledCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, Collections.singletonMap("Environment", "Stage"), null,
                null);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withServiceResult()));
        when(client.configMapByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.LABELED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith("mysql-dbms"));
    }

    @Test
    void shouldFailWhenNoLabelsProvidedForLabeledCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, Collections.emptyMap(), null, null);
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theRequiredCapability));
    }

    @Test
    void shouldProvideSpecifiedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.SPECIFIED, Collections.emptyMap(), null,
                new ResourceReference("my-db-namespace", "my-db"));
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withServiceResult()));
        when(client.configMapByName("my-db-namespace", "my-db"))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.SPECIFIED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is("my-db-namespace"));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("my-db"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is("my-db-namespace"));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith("my-db"));
    }

    @Test
    void shouldFailWhenNoReferenceSpecifiedForSpecifiedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.SPECIFIED, Collections.emptyMap(), null, null);
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theRequiredCapability));
    }

    @Test
    void shouldProvideNamespaceScopeCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.NAMESPACE, null, null, null);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withServiceResult()));
        when(client.configMapByLabels(eq(forResource.getMetadata().getNamespace()), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.NAMESPACE));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), is("default-mysql-dbms"));

    }

    @Test
    void shouldProvideDedicatedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.DEDICATED, Collections.emptyMap(), null, null);
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withServiceResult()));
        when(client.configMapByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.DEDICATED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-db"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith(forResource.getMetadata().getName() + "-db"));
    }

    @Test
    void shouldProvideDedicatedCapabilityWithIngress() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a Keycloak server
        final RequiredCapability theRequiredCapability = new RequiredCapability(StandardCapability.SSO,
                StandardCapabilityImplementation.KEYCLOAK, CapabilityScope.DEDICATED, Collections.emptyMap(), null, null);
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theRequiredCapability, withExposedServiceResult()));
        when(client.configMapByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource, theRequiredCapability);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getCapability(), is(StandardCapability.SSO));
        assertThat(providedCapability.getImplementation(), is(StandardCapabilityImplementation.KEYCLOAK));
        assertThat(providedCapability.getCapabilityProvisionScope(), is(CapabilityScope.DEDICATED));
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
        assertThat(providedCapability.getIngressReference().get().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getIngressReference().get().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
    }

    private Function<CapabilityRequirement, ServiceResult> withExposedServiceResult() {
        return (capabilityRequirement) -> new SampleExposedDeploymentResult(null, new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName())
                .endMetadata()
                .build(),
                new IngressBuilder()
                        .withNewMetadata()
                        .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                        .withName(capabilityRequirement.getMetadata().getName())
                        .endMetadata()
                        .build());
    }

    private Answer<?> andGenerateSuccessEventFor(RequiredCapability requiredCapability,
            Function<CapabilityRequirement, ServiceResult> serviceResultSupplier) {
        return invocationOnMock -> {

            scheduler.schedule(() -> {
                CapabilityRequirement capabilityRequirement = (CapabilityRequirement) invocationOnMock.getArguments()[0];
                foundCapability = new ProvidedCapability(requiredCapability.getImplementation().get(),
                        requiredCapability.getCapabilityRequirementScope().get())
                        .toConfigMap(capabilityRequirement,
                                serviceResultSupplier.apply(capabilityRequirement),

                                new SecretBuilder()
                                        .withNewMetadata()
                                        .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                                        .withName(capabilityRequirement.getMetadata().getName())
                                        .endMetadata()
                                        .build()
                        );
                final CapabilityRequirement resource = new CapabilityRequirement(new ObjectMeta(), requiredCapability);
                resource.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.SUCCESSFUL, 1L);
                ((CapabilityRequirementWatcher) invocationOnMock.getArguments()[1]).eventReceived(Action.MODIFIED, resource);

            }, 300, TimeUnit.MILLISECONDS);
            return (Watch) () -> {

            };
        };
    }

    private Answer<?> andGenerateFailEvent() {
        return invocationOnMock -> {
            scheduler.schedule(() -> {
                ((CapabilityRequirementWatcher) invocationOnMock.getArguments()[1]).onClose(new WatcherException("ASdf"));

            }, 300, TimeUnit.MILLISECONDS);
            return (Watch) () -> {
            };
        };
    }
}
