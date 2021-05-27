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

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.spi.result.ServiceResult;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;
import org.entando.kubernetes.controller.support.command.ProvideCapabilityCommand;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ExposedServerStatus;
import org.entando.kubernetes.model.common.InternalServerStatus;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.test.common.DatabaseDeploymentResult;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("inner-hexagon")})
class ProvideCapabilityCommandTest implements InProcessTestData {

    public static final int TIMEOUT_SECONDS = 30;
    SimpleK8SClientDouble clientDouble = new SimpleK8SClientDouble();
    CapabilityClient capabilityClient = clientDouble.capabilities();
    SerializingCapabilityProvider capabilityProvider = new SerializingCapabilityProvider(clientDouble.entandoResources(),
            new InProcessCommandStream(clientDouble, null));
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private ProvidedCapability foundCapability;
    private static final String OPERATOR_NAMESPACE = "entando-operator";

    @BeforeEach
    void beforeEach() {
        step("Given I have registered a CustomResourceDefinition for the resource kind 'TestResource'", () -> {
            clientDouble.entandoResources().registerCustomResourceDefinition("testrources.test.org.crd.yaml");
        });
    }

    @Test
    void shouldProvideClusterScopeCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(
                        StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.CLUSTER)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        when(capabilityClient.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        doAnswer(andGenerateSuccessEventFor(withServiceResult())).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient)
                .execute(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.CLUSTER));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(OPERATOR_NAMESPACE));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms-in-cluster"));
    }

    private Function<ProvidedCapability, ServiceResult> withServiceResult() {
        return (capabilityRequirement) -> new DatabaseDeploymentResult(new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName())
                .endMetadata()
                .build(), DbmsVendorConfig.MYSQL, "my_db",
                capabilityRequirement.getMetadata().getName(), null);
    }

    @Test
    void shouldFailWhenTheCapabilityStatusPhaseIsFailed() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL)
                .withCapabilityRequirementScope(CapabilityScope.CLUSTER)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        when(capabilityClient.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        doAnswer(andGenerateFailEvent()).when(capabilityClient).createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        assertThrows(EntandoControllerException.class,
                () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS));
    }

    @Test
    void shouldFailWhenThereIsAScopeMismatch() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL)
                .withCapabilityRequirementScope(CapabilityScope.LABELED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                .withSelector(Collections.singletonMap("Environment", "Stage")).build();
        when(capabilityClient.providedCapabilityByLabels(Collections.singletonMap("Environment", "Stage")))
                .thenReturn(Optional.of(new ProvidedCapability(new ObjectMetaBuilder()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME, CapabilityScope.DEDICATED.getCamelCaseName())
                        .build(), new CapabilityRequirement())));
        //When I attempt to fulfill the capability
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement,
                TIMEOUT_SECONDS));
    }

    @Test
    void shouldFailWhenThereIsAnImplementationMismatch() {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL)
                .withCapabilityRequirementScope(CapabilityScope.LABELED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                .withSelector(Collections.singletonMap("Environment", "Stage"))
                .build();
        when(capabilityClient.providedCapabilityByLabels(Collections.singletonMap("Environment", "Stage")))
                .thenReturn(Optional.of(new ProvidedCapability(new ObjectMetaBuilder()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME,
                                CapabilityScope.LABELED.getCamelCaseName())
                        .addToLabels(ProvidedCapability.IMPLEMENTATION_LABEL_NAME,
                                StandardCapabilityImplementation.POSTGRESQL.getCamelCaseName())
                        .build(), new CapabilityRequirement())));
        //When I attempt to fulfill the capability
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement,
                TIMEOUT_SECONDS));
    }

    @Test
    void shouldProvideLabeledCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.LABELED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                .withSelector(Collections.singletonMap("Environment", "Stage")).build();
        doAnswer(andGenerateSuccessEventFor(withServiceResult())).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByLabels(Collections.singletonMap("Environment", "Stage")))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient).execute(forResource,
                theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.LABELED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("mysql-dbms"));
        assertThat(providedCapability.getMetadata().getLabels().get("Environment"), is("Stage"));
    }

    @Test
    void shouldFailWhenNoLabelsProvidedForLabeledCapability() {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.LABELED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        //When I attempt to fulfill the capability
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement,
                TIMEOUT_SECONDS));
    }

    @Test
    void shouldProvideSpecifiedCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.SPECIFIED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                .withSpecifiedCapability(
                        new ResourceReference("my-db-namespace", "my-db")).build();
        //When I attempt to fulfill the capability
        doAnswer(andGenerateSuccessEventFor(withServiceResult())).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByName("my-db-namespace", "my-db"))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient).execute(forResource,
                theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.SPECIFIED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is("my-db-namespace"));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("my-db"));
    }

    @Test
    void shouldFailWhenNoReferenceSpecifiedForSpecifiedCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.SPECIFIED)
                .withProvisioningStrategy(
                        CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        //When I attempt to fulfill the capability
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement,
                TIMEOUT_SECONDS));
    }

    @Test
    void shouldProvideNamespaceScopeCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.NAMESPACE)
                .withProvisioningStrategy(
                        CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        doAnswer(andGenerateSuccessEventFor(withServiceResult())).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByLabels(eq(forResource.getMetadata().getNamespace()), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient).execute(forResource,
                theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.NAMESPACE));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms-in-namespace"));

    }

    @Test
    void shouldProvideDedicatedCapability() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.DBMS)
                .withImplementation(
                        StandardCapabilityImplementation.MYSQL).withCapabilityRequirementScope(CapabilityScope.DEDICATED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        //When I attempt to fulfill the capability
        doAnswer(andGenerateSuccessEventFor(withServiceResult()
        )).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient).execute(forResource,
                theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.DEDICATED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-db"));
    }

    @Test
    void shouldProvideDedicatedCapabilityWithIngress() throws TimeoutException {
        //Given I have an TestResource
        final TestResource forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());

        //with a cluster scoped capability requirement for a Keycloak server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.SSO)
                .withImplementation(
                        StandardCapabilityImplementation.KEYCLOAK).withCapabilityRequirementScope(CapabilityScope.DEDICATED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        //When I attempt to fulfill the capability
        doAnswer(andGenerateSuccessEventFor(withExposedServiceResult())).when(capabilityClient)
                .createAndWaitForCapability(any(), eq(TIMEOUT_SECONDS));
        when(capabilityClient.providedCapabilityByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        when(capabilityClient.buildCapabilityProvisioningResult(any()))
                .thenAnswer(invocationOnMock -> new SerializedCapabilityProvisioningResult(foundCapability, null, null, null));
        //When I attempt to fulfill the capability
        final SerializedCapabilityProvisioningResult capabilityResult = new ProvideCapabilityCommand(capabilityClient).execute(forResource,
                theCapabilityRequirement, TIMEOUT_SECONDS);
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.SSO));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.KEYCLOAK));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.DEDICATED));
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
        assertThat(providedCapability.getIngressReference().get().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getIngressReference().get().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
    }

    private Function<ProvidedCapability, ServiceResult> withExposedServiceResult() {
        return (capabilityRequirement) -> new DefaultExposedDeploymentResult(null, new ServiceBuilder()
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

    private Answer<?> andGenerateSuccessEventFor(Function<ProvidedCapability, ServiceResult> serviceResultSupplier) {
        return invocationOnMock -> {
            scheduler.schedule(() -> {
                try {
                    foundCapability = (ProvidedCapability) invocationOnMock.getArguments()[0];
                    foundCapability.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.SUCCESSFUL, 1L);
                    final ServiceResult serviceResult = serviceResultSupplier.apply(foundCapability);
                    AbstractServerStatus status;
                    if (serviceResult instanceof ExposedService) {
                        status = new ExposedServerStatus("server");
                        ((ExposedServerStatus) status)
                                .setIngressName(((ExposedService) serviceResult).getIngress().getMetadata().getName());
                    } else {
                        status = new InternalServerStatus("server");
                    }
                    status.setServiceName(serviceResult.getService().getMetadata().getName());
                    foundCapability.getStatus().putServerStatus(status);
                    clientDouble.entandoResources().createOrPatchEntandoResource(foundCapability);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

            }, 300, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    private Answer<?> andGenerateFailEvent() {
        return invocationOnMock -> {
            scheduler.schedule(() -> {
                ProvidedCapability capability = invocationOnMock.getArgument(0);
                clientDouble.entandoResources().deploymentFailed(capability, new IllegalStateException());
            }, 300, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }
}
