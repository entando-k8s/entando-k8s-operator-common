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

package org.entando.kubernetes.controller.inprocesstest.legacy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.common.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.app.DoneableEntandoApp;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.junit.jupiter.api.AfterEach;
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
@Tags({@Tag("in-process"), @Tag("pre-deploymentt")})
//Sonar doesn't recognize custom captors
@SuppressWarnings({"java:S6068", "java:S6073"})
class DeployDatabaseTest implements InProcessTestUtil, FluentTraversals {

    private static final String MY_APP_DB = MY_APP + "-db";
    private static final String MY_APP_DB_SERVICE = MY_APP_DB + "-service";
    private static final String MY_APP_DB_PVC = MY_APP_DB + "-pvc";
    private static final String MY_APP_DB_DEPLOYMENT = MY_APP_DB + "-deployment";
    private static final String MY_APP_DB_SECRET = MY_APP_DB + "-secret";
    private static final String MY_APP_DB_ADMIN_SECRET = MY_APP_DB + "-admin-secret";
    private static final String MY_APP_DATABASE = "my_app_db";
    private final EntandoApp entandoApp = newTestEntandoApp();
    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    private SampleController<EntandoAppSpec, EntandoApp, SampleExposedDeploymentResult> sampleController;

    @BeforeEach
    void before() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE.getJvmSystemProperty(), "true");
        this.sampleController = new SampleController<>(client, keycloakClient) {
            @Override
            protected Deployable<SampleExposedDeploymentResult, EntandoAppSpec> createDeployable(
                    EntandoApp newEntandoApp,
                    DatabaseServiceResult databaseServiceResult, KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SamplePublicIngressingDbAwareDeployable<>(newEntandoApp, databaseServiceResult,
                        keycloakConnectionConfig);
            }
        };
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, entandoApp.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, entandoApp.getMetadata().getName());
        emulateKeycloakDeployment(client);
    }

    @AfterEach
    void cleanup() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE.getJvmSystemProperty());

    }

    @Test
    void testSecrets() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        //Given I have an EntandoApp custom resource with MySQL as database
        final EntandoApp newEntandoApp = entandoApp;
        client.entandoResources().createOrPatchEntandoResource(entandoApp);
        // When I  deploy the EntandoApp
        sampleController.onStartup(new StartupEvent());

        //Then a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is an admin secret
        NamedArgumentCaptor<Secret> adminSecretCaptor = forResourceNamed(Secret.class, MY_APP_DB_ADMIN_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newEntandoApp), adminSecretCaptor.capture());
        Secret theDbAdminSecret = adminSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(theDbAdminSecret), is("root"));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(theDbAdminSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(theDbAdminSecret), is(MY_APP));

        //And a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is the keycloakd db secret
        NamedArgumentCaptor<Secret> keycloakDbSecretCaptor = forResourceNamed(Secret.class, MY_APP_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newEntandoApp), keycloakDbSecretCaptor.capture());
        Secret keycloakDbSecret = keycloakDbSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(keycloakDbSecret), is(MY_APP_DATABASE));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(keycloakDbSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(keycloakDbSecret), is(MY_APP));

    }

    @Test
    void testService() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = entandoApp;
        client.entandoResources().createOrPatchEntandoResource(entandoApp);
        //And that K8S is up and receiving Service requests
        ServiceStatus dbServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newEntandoApp), eq(MY_APP_DB_SERVICE)))
                .then(respondWithServiceStatus(dbServiceStatus));

        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());
        //Then a K8S Service was created with a name that reflects the EntandoApp and the fact that it is a JEE service
        NamedArgumentCaptor<Service> dbServiceCaptor = forResourceNamed(Service.class, MY_APP_DB_SERVICE);
        verify(client.services()).createOrReplaceService(eq(newEntandoApp), dbServiceCaptor.capture());
        //And a selector that matches the Keyclaok DB pod
        Service dbService = dbServiceCaptor.getValue();
        Map<String, String> dbSelector = dbService.getSpec().getSelector();
        assertThat(dbSelector.get(DEPLOYMENT_LABEL_NAME), is(MY_APP_DB));
        assertThat(dbSelector.get(ENTANDO_APP_LABEL_NAME), is(MY_APP));
        //And the TCP port 3306 named 'db-port'
        assertThat(thePortNamed(DB_PORT).on(dbService).getPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(dbService).getProtocol(), is(TCP));
        //And the state of the two services was reloaded from K8S
        verify(client.services()).loadService(eq(newEntandoApp), eq(MY_APP_DB_SERVICE));
        //And K8S was instructed to update the status of the EntandoApp with the status of the java service
        //And the db service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesServiceStatus(dbServiceStatus)));
    }

    @Test
    void testMysqlDeployment() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = entandoApp;
        //And a name longer than 32 chars
        newEntandoApp.getMetadata().setName(MY_APP + "-name-longer-than-32-characters");
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, entandoApp.getMetadata().getName());
        client.entandoResources().createOrPatchEntandoResource(entandoApp);
        //And K8S is receiving Deployment requests
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newEntandoApp), eq(MY_APP + "-name-longer-than-32-characters-db-deployment")))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());

        //Then two K8S deployments are created with a name that reflects the EntandoApp name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP + "-name-longer-than-32-characters-db-deployment");
        verify(client.deployments()).createOrPatchDeployment(eq(newEntandoApp), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        Container theDbContainer = theContainerNamed("db-container").on(dbDeployment);
        NamedArgumentCaptor<Deployment> serverDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP + "-name-longer-than-32-characters-server-deployment");
        verify(client.deployments()).createOrPatchDeployment(eq(newEntandoApp), serverDeploymentCaptor.capture());
        Deployment serverDeployment = serverDeploymentCaptor.getValue();
        String database = theVariableNamed("DB_DATABASE").on(thePrimaryContainerOn(serverDeployment));
        assertThat(database.length(), is(32));
        //Exposing a port 3306
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/mysql-80-centos7:latest"));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()),
                is(MY_APP + "-name-longer-than-32-characters-db"));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()),
                is(MY_APP + "-name-longer-than-32-characters"));
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getSecurityContext().getFsGroup(), is(27L));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newEntandoApp), eq(MY_APP + "-name-longer-than-32-characters-db-deployment"));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesDeploymentStatus(dbDeploymentStatus)));

        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newEntandoApp, client, dbDeployment);
        //And a shortened username has been specified for the MySQL db
        NamedArgumentCaptor<Secret> dbSecretCaptor = forResourceNamed(Secret.class,
                MY_APP + "-name-longer-than-32-characters-db-secret");
        verify(client.secrets()).createSecretIfAbsent(eq(newEntandoApp), dbSecretCaptor.capture());
        Secret keycloakDbSecret = dbSecretCaptor.getValue();
        assertThat(keycloakDbSecret.getStringData().get(KubeUtils.USERNAME_KEY).length(), is(32));
        assertThat(thePrimaryContainerOn(dbDeployment).getReadinessProbe().getExec().getCommand().get(3),
                is("MYSQL_PWD=${MYSQL_ROOT_PASSWORD} mysql -h 127.0.0.1 -u root -e 'SELECT 1'"));
    }

    @Test
    void testPostgresqlDeployment() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = new DoneableEntandoApp(entandoApp, s -> s)
                .editSpec()
                .withDbms(DbmsVendor.POSTGRESQL)
                .endSpec()
                .done();
        client.entandoResources().createOrPatchEntandoResource(newEntandoApp);
        //And K8S is receiving Deployment requests
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newEntandoApp), eq(MY_APP_DB_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());

        //Then two K8S deployments are created with a name that reflects the EntandoApp name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP_DB_DEPLOYMENT);
        verify(client.deployments()).createOrPatchDeployment(eq(newEntandoApp), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        Container theDbContainer = theContainerNamed("db-container").on(dbDeployment);
        //Exposing a port 5432
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(5432));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/postgresql-12-centos7:latest"));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_APP_DB));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_APP));
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getSecurityContext().getFsGroup(), is(26L));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newEntandoApp), eq(MY_APP_DB_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesDeploymentStatus(dbDeploymentStatus)));
        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newEntandoApp, client, dbDeployment);
        assertThat(thePrimaryContainerOn(dbDeployment).getReadinessProbe().getExec().getCommand().get(3), is("psql -h 127.0.0.1 -U "
                + "${POSTGRESQL_USER} -q -d postgres -c '\\l'|grep ${POSTGRESQL_DATABASE}"));
    }

    @Test
    void testPersistentVolumeClaims() {
        //Given I have  a Keycloak server
        EntandoApp newEntandoApp = this.entandoApp;
        client.entandoResources().createOrPatchEntandoResource(newEntandoApp);
        //And that K8S is up and receiving PVC requests
        PersistentVolumeClaimStatus dbPvcStatus = new PersistentVolumeClaimStatus();
        lenient().when(client.persistentVolumeClaims()
                .loadPersistentVolumeClaim(eq(newEntandoApp), eq(MY_APP_DB_PVC)))
                .then(respondWithPersistentVolumeClaimStatus(dbPvcStatus));

        //When the KeycloakController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());

        //Then K8S was instructed to create a PersistentVolumeClaim for the DB and the JEE Server
        NamedArgumentCaptor<PersistentVolumeClaim> dbPvcCaptor = forResourceNamed(PersistentVolumeClaim.class,
                MY_APP_DB_PVC);
        verify(this.client.persistentVolumeClaims())
                .createPersistentVolumeClaimIfAbsent(eq(newEntandoApp), dbPvcCaptor.capture());
        //With names that reflect the EntandoPlugin and the type of deployment the claim is used for
        PersistentVolumeClaim dbPvc = dbPvcCaptor.getValue();

        //And labels that link this PVC to the EntandoApp, the EntandoPlugin and the specific deployment
        assertThat(dbPvc.getMetadata().getLabels().get(ENTANDO_APP_LABEL_NAME), is(MY_APP));
        assertThat(dbPvc.getMetadata().getLabels().get(DEPLOYMENT_LABEL_NAME), is(MY_APP_DB));

        //And both PersistentVolumeClaims were reloaded from  K8S for its latest state
        verify(this.client.persistentVolumeClaims())
                .loadPersistentVolumeClaim(eq(newEntandoApp), eq(MY_APP_DB_PVC));

        // And K8S was instructed to update the status of the EntandoPlugin with
        // the status of both PersistentVolumeClaims
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(containsThePersistentVolumeClaimStatus(dbPvcStatus)));
    }

}
