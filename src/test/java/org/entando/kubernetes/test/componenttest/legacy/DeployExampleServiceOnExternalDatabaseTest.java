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

package org.entando.kubernetes.test.componenttest.legacy;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.examples.SampleController;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.controller.EntandoDatabaseServiceController;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.componenttest.InProcessTestUtil;
import org.entando.kubernetes.test.componenttest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.test.componenttest.argumentcaptors.NamedArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
//in execute component test
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
//Sonar doesn't recognize custom captors
@SuppressWarnings({"java:S6068", "java:S6073"})
class DeployExampleServiceOnExternalDatabaseTest implements InProcessTestUtil, FluentTraversals, CommonLabels {

    public static final String MY_APP_SERVER_DEPLOYMENT = MY_APP + "-server-deployment";
    private static final String MY_APP_DB_SECRET = MY_APP + "-db-secret";
    private final EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp())
            .editSpec()
            .withDbms(DbmsVendor.ORACLE)
            .endSpec()
            .build();
    private final EntandoDatabaseService externalDatabase = buildEntandoDatabaseService();
    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    private SampleController<EntandoAppSpec, EntandoApp, SampleExposedDeploymentResult> sampleController;

    @BeforeEach
    void prepareExternalDB() {
        this.sampleController = new SampleController<>(client, keycloakClient) {
            @Override
            protected Deployable<SampleExposedDeploymentResult> createDeployable(
                    EntandoApp newEntandoApp,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SamplePublicIngressingDbAwareDeployable<>(newEntandoApp, databaseServiceResult,
                        keycloakConnectionConfig);
            }
        };
        emulateKeycloakDeployment(client);
        externalDatabase.getMetadata().setNamespace(entandoApp.getMetadata().getNamespace());
        client.entandoResources().putEntandoDatabaseService(externalDatabase);
        new EntandoDatabaseServiceController(client).processEvent(Action.ADDED, externalDatabase);
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, entandoApp.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, entandoApp.getMetadata().getName());

        client.entandoResources().createOrPatchEntandoResource(entandoApp);
    }

    @Test
    void testSecrets() {
        //Given I have created an EntandoDatabaseService custom resource
        //When I deploy a EntandoApp
        sampleController.onStartup(new StartupEvent());
        //Then a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is a secret
        NamedArgumentCaptor<Secret> keycloakSecretCaptor = forResourceNamed(Secret.class, MY_APP_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(entandoApp), keycloakSecretCaptor.capture());
        Secret keycloakSecret = keycloakSecretCaptor.getValue();
        assertThat(keycloakSecret.getStringData().get(SecretUtils.USERNAME_KEY), is("my_app_db"));
        assertThat(keycloakSecret.getStringData().get(SecretUtils.PASSSWORD_KEY), is(not(emptyOrNullString())));
    }

    @Test
    void testDeployment() {
        //Given I have created an EntandoDatabaseService custom resource
        //And Keycloak is receiving requests
        lenient().when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn(KEYCLOAK_SECRET);
        //When I deploy a EntandoApp
        sampleController.onStartup(new StartupEvent());

        //Then a K8S deployment is created
        NamedArgumentCaptor<Deployment> keyclaokDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP_SERVER_DEPLOYMENT);
        verify(client.deployments()).createOrPatchDeployment(eq(entandoApp), keyclaokDeploymentCaptor.capture());
        //Then a pod was created for an Entandoapp using the credentials and connection settings of the EntandoDatabaseService
        LabeledArgumentCaptor<Pod> keycloakSchemaJobCaptor = forResourceWithLabels(Pod.class, dbPreparationJobLabels(entandoApp, "server"));
        verify(client.pods()).runToCompletion(keycloakSchemaJobCaptor.capture());
        Pod keycloakDbJob = keycloakSchemaJobCaptor.getValue();
        Container theInitContainer = theInitContainerNamed(MY_APP + "-db-schema-creation-job").on(keycloakDbJob);
        verifyStandardSchemaCreationVariables("my-secret", MY_APP_DB_SECRET, theInitContainer, DbmsVendor.ORACLE);
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(theInitContainer),
                is("mydb-db-service." + MY_APP_NAMESPACE + ".svc.cluster.local"));
        //And it was instructed to create a schema reflecting the keycloakdb user
        assertThat(theVariableNamed(DATABASE_NAME).on(theInitContainer), is("my_db"));
    }

    private EntandoDatabaseService buildEntandoDatabaseService() {
        return new EntandoDatabaseServiceBuilder()
                .withNewMetadata().withName("mydb").withNamespace("mynamespace").endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.ORACLE)
                .withHost("myoracle.com")
                .withPort(1521)
                .withDatabaseName("my_db")
                .withSecretName("my-secret")
                .addToJdbcParameters("some.param", "some.value")
                .endSpec().build();
    }
}
