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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressStatus;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Map;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.examples.SampleController;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.creators.SecretCreator;
import org.entando.kubernetes.controller.support.creators.TlsHelper;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.componenttest.InProcessTestUtil;
import org.entando.kubernetes.test.componenttest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.test.componenttest.argumentcaptors.NamedArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
class DeployExampleServiceTest implements InProcessTestUtil, FluentTraversals, CommonLabels {

    private static final String MY_APP_ADMIN_SECRET = MY_APP + "-admin-secret";
    private static final String MY_APP_SERVER = MY_APP + "-server";
    private static final String MY_APP_SERVER_SERVICE = MY_APP_SERVER + "-service";
    private static final String MY_APP_SERVER_DEPLOYMENT = MY_APP_SERVER + "-deployment";
    private static final String MY_APP_DB = MY_APP + "-db";
    private static final String MY_APP_DB_SERVICE = MY_APP_DB + "-service";
    private static final String MY_APP_DB_PVC = MY_APP_DB + "-pvc";
    private static final String MY_APP_DB_DEPLOYMENT = MY_APP_DB + "-deployment";
    private static final String MY_APP_DB_SECRET = MY_APP_DB + "-secret";
    private static final String MY_APP_INGRESS = MY_APP + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;
    private static final String MY_APP_DB_ADMIN_SECRET = MY_APP_DB + "-admin-secret";
    private static final String DB_ADDR = "DB_ADDR";
    private static final String DB_PORT_VAR = "DB_PORT";
    private static final String DB_DATABASE = "DB_DATABASE";
    private static final String DB_USER = "DB_USER";
    private static final String DB_PASSWORD = "DB_PASSWORD";
    private static final String MY_APP_DATABASE = "my_app_db";
    private static final String AUTH = "/auth";
    private final EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp()).editSpec().withTlsSecretName(null).endSpec().build();
    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    private SampleController<EntandoAppSpec, EntandoApp, SampleExposedDeploymentResult> sampleController;

    @BeforeEach
    void prepareKeycloakCustomResource() {
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
        client.entandoResources().createOrPatchEntandoResource(entandoApp);
        emulateKeycloakDeployment(client);
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, entandoApp.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, entandoApp.getMetadata().getName());
    }

    @AfterEach
    void resetSystemProps() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.getJvmSystemProperty());
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.getJvmSystemProperty());
        TlsHelper.getInstance().init();
    }

    @Test
    void testSecrets() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        //Given I have an EntandoApp custom resource with MySQL as database
        final EntandoApp newEntandoApp = entandoApp;
        //And the trust cert has been configured correctly
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.getJvmSystemProperty(),
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net", "ca.crt").normalize().toAbsolutePath().toString());
        //And the default TLS Keypair has been configured correctly
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_PATH_TO_TLS_KEYPAIR.getJvmSystemProperty(),
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net").normalize().toAbsolutePath().toString());
        TlsHelper.getInstance().init();
        Assertions.assertTrue(TlsHelper.getInstance().isTrustStoreAvailable());
        // WHen I have deploya the EntandoApp
        sampleController.onStartup(new StartupEvent());

        //Then a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is an admin secret
        NamedArgumentCaptor<Secret> adminSecretCaptor = forResourceNamed(Secret.class, MY_APP_DB_ADMIN_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newEntandoApp), adminSecretCaptor.capture());
        Secret theDbAdminSecret = adminSecretCaptor.getValue();
        assertThat(theKey(SecretUtils.USERNAME_KEY).on(theDbAdminSecret), is("root"));
        assertThat(theKey(SecretUtils.PASSSWORD_KEY).on(theDbAdminSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(theDbAdminSecret), is(MY_APP));

        //And a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is the keycloakd db secret
        NamedArgumentCaptor<Secret> entandoAppDbSecretCaptor = forResourceNamed(Secret.class, MY_APP_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newEntandoApp), entandoAppDbSecretCaptor.capture());
        Secret entandoAppDbSecret = entandoAppDbSecretCaptor.getValue();
        assertThat(theKey(SecretUtils.USERNAME_KEY).on(entandoAppDbSecret), is(MY_APP_DATABASE));
        assertThat(theKey(SecretUtils.PASSSWORD_KEY).on(entandoAppDbSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(entandoAppDbSecret), is(MY_APP));

        //And a K8S Secret was created in the Keycloak deployment's namespace containing the CA keystore
        NamedArgumentCaptor<Secret> tlsSecretCaptor = forResourceNamed(Secret.class, MY_APP + "-tls-secret");
        verify(client.secrets(), atLeast(1)).createSecretIfAbsent(eq(newEntandoApp), tlsSecretCaptor.capture());
        Secret tlsSecret = tlsSecretCaptor.getValue();
        assertThat(theKey(TlsHelper.TLS_KEY).on(tlsSecret), is(TlsHelper.getInstance().getTlsKeyBase64()));
        assertThat(theKey(TlsHelper.TLS_CRT).on(tlsSecret), is(TlsHelper.getInstance().getTlsCertBase64()));

        //And a K8S Secret was created in the Keycloak deployment's namespace containing the CA keystore
        NamedArgumentCaptor<Secret> trustStoreSecretCaptor = forResourceNamed(Secret.class,
                TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME);
        verify(client.secrets(), atLeast(1)).createSecretIfAbsent(eq(newEntandoApp), trustStoreSecretCaptor.capture());
        Secret trustStoreSecret = trustStoreSecretCaptor.getValue();
        assertThat(theKey(SecretCreator.TRUST_STORE_FILE).on(trustStoreSecret), is(TlsHelper.getInstance().getTrustStoreBase64()));

        byte[] decode = Base64.getDecoder().decode(trustStoreSecret.getData().get(SecretCreator.TRUST_STORE_FILE).getBytes());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(new ByteArrayInputStream(decode), TlsHelper.getInstance().getTrustStorePassword().toCharArray());
        assertNotNull(ks.getCertificate("ca.crt"));

    }

    @Test
    void testService() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = entandoApp;
        //And that K8S is up and receiving Service requests
        ServiceStatus dbServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newEntandoApp), eq(MY_APP_DB_SERVICE)))
                .then(respondWithServiceStatus(dbServiceStatus));
        ServiceStatus javaServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newEntandoApp), eq(MY_APP_SERVER_SERVICE)))
                .then(respondWithServiceStatus(javaServiceStatus));

        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());
        //Then a K8S Service was created with a name that reflects the EntandoApp and the fact that it is a JEE service
        NamedArgumentCaptor<Service> dbServiceCaptor = forResourceNamed(Service.class, MY_APP_DB_SERVICE);
        verify(client.services()).createOrReplaceService(eq(newEntandoApp), dbServiceCaptor.capture());
        NamedArgumentCaptor<Service> serverServiceCaptor = forResourceNamed(Service.class, MY_APP_SERVER_SERVICE);
        verify(client.services()).createOrReplaceService(eq(newEntandoApp), serverServiceCaptor.capture());
        //And a selector that matches the EntandoApp pod
        Service serverService = serverServiceCaptor.getValue();
        Map<String, String> serverSelector = serverService.getSpec().getSelector();
        assertThat(serverSelector.get(DEPLOYMENT_LABEL_NAME), is(MY_APP_SERVER));
        assertThat(serverSelector.get(ENTANDO_APP_LABEL_NAME), is(MY_APP));
        //And the TCP port 8080 named 'server-port'
        assertThat(thePortNamed(SERVER_PORT).on(serverService).getPort(), is(8080));
        assertThat(thePortNamed(SERVER_PORT).on(serverService).getProtocol(), is(TCP));
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
        verify(client.services()).loadService(eq(newEntandoApp), eq(MY_APP_SERVER_SERVICE));
        //And K8S was instructed to update the status of the EntandoApp with the status of the java service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesServiceStatus(javaServiceStatus)));
        //And the db service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesServiceStatus(dbServiceStatus)));
    }

    @Test
    void testIngress() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = entandoApp;
        //And that K8S is up and receiving Ingress requests
        IngressStatus ingressStatus = new IngressStatus();

        when(client.ingresses().loadIngress(eq(newEntandoApp.getMetadata().getNamespace()), any(String.class)))
                .thenAnswer(respondWithIngressStatusForPath(ingressStatus, AUTH));

        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());
        // Then a K8S Ingress Path was created with a name that reflects the name of the EntandoApp and
        // the fact that it is a the Keycloak path
        NamedArgumentCaptor<Ingress> ingressArgumentCaptor = forResourceNamed(Ingress.class, MY_APP_INGRESS);
        verify(client.ingresses()).createIngress(eq(newEntandoApp), ingressArgumentCaptor.capture());
        Ingress resultingIngress = ingressArgumentCaptor.getValue();
        //With a path that reflects webcontext of Keycloak, mapped to the previously created service
        assertThat(theBackendFor(AUTH).on(resultingIngress).getServicePort().getIntVal(), is(8080));
        assertThat(theBackendFor(AUTH).on(resultingIngress).getServiceName(), is(MY_APP_SERVER_SERVICE));
        //And the Ingress state was reloaded from K8S
        verify(client.ingresses(), times(2))
                .loadIngress(eq(newEntandoApp.getMetadata().getNamespace()), eq(MY_APP_INGRESS));

        //And K8S was instructed to update the status of the EntandoApp with the status of the ingress
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesIngressStatus(ingressStatus)));
    }

    @Test
    void testSchemaPreparation() {
        //Given I have an EntandoApp custom resource with MySQL as database
        EntandoApp newEntandoApp = entandoApp;

        //When the DeployCommand processes the addition request
        sampleController.onStartup(new StartupEvent());
        // A DB preparation Pod is created with labels linking it to the EntandoApp
        LabeledArgumentCaptor<Pod> podCaptor = forResourceWithLabels(Pod.class, dbPreparationJobLabels(newEntandoApp, "server"));
        verify(client.pods()).runToCompletion(podCaptor.capture());
        Pod theDbJobPod = podCaptor.getValue();
        //With exactly 1 container
        assertThat(theDbJobPod.getSpec().getInitContainers().size(), is(1));
        //And the DB Schema Preparation Container is configured with the appropriate Environment Variables
        Container theSchemaPeparationContainer = theInitContainerNamed(MY_APP_DB + "-schema-creation-job").on(theDbJobPod);
        assertThat(theVariableNamed(DATABASE_SCHEMA_COMMAND).on(theSchemaPeparationContainer), is("CREATE_SCHEMA"));
        assertThat(theVariableNamed(DATABASE_NAME).on(theSchemaPeparationContainer), is(MY_APP_DATABASE));
        assertThat(theVariableNamed(DATABASE_VENDOR).on(theSchemaPeparationContainer), is("mysql"));
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(theSchemaPeparationContainer),
                is(MY_APP_DB_SERVICE + "." + MY_APP_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DATABASE_SERVER_PORT).on(theSchemaPeparationContainer), is("3306"));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_APP_DB_ADMIN_SECRET));
        assertThat(
                theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_APP_DB_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_APP_DB_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_APP_DB_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
    }

    @Test
    void testDeployment() {
        //Given I have an EntandoApp custom resource with MySQL as database
        final EntandoApp newEntandoApp = entandoApp;
        emulateKeycloakDeployment(client);
        //And the trust cert has been configured correctly
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_CA_CERT_PATHS.getJvmSystemProperty(),
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net", "ca.crt").normalize().toAbsolutePath().toString());
        TlsHelper.getInstance().init();
        //And K8S is receiving Deployment requests
        DeploymentStatus serverDeploymentStatus = new DeploymentStatus();
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newEntandoApp), eq(MY_APP_DB_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newEntandoApp), eq(MY_APP_SERVER_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(serverDeploymentStatus));

        //When the the EntandoAppController is notified that a new EntandoApp has been added
        sampleController.onStartup(new StartupEvent());

        //Then two K8S deployments are created with a name that reflects the EntandoApp name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP_DB_DEPLOYMENT);
        verify(client.deployments()).createOrPatchDeployment(eq(newEntandoApp), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        verifyTheDbContainer(theContainerNamed("db-container").on(dbDeployment));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_APP_DB));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()),
                is(MY_APP));

        // And a ServerDeployment
        NamedArgumentCaptor<Deployment> serverDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_APP_SERVER_DEPLOYMENT);
        verify(client.deployments()).createOrPatchDeployment(eq(newEntandoApp), serverDeploymentCaptor.capture());
        Deployment serverDeployment = serverDeploymentCaptor.getValue();
        //With a Pod Template that has labels linking it to the previously created K8S  Keycloak Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(serverDeployment.getSpec().getTemplate()), is(MY_APP_SERVER));
        assertThat(theLabel(ENTANDO_APP_LABEL_NAME).on(serverDeployment.getSpec().getTemplate()), is(MY_APP));
        verifyTheServerContainer(theContainerNamed("server-container").on(serverDeployment));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newEntandoApp), eq(MY_APP_DB_DEPLOYMENT));
        verify(client.deployments()).loadDeployment(eq(newEntandoApp), eq(MY_APP_SERVER_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesDeploymentStatus(dbDeploymentStatus)));
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoApp), argThat(matchesDeploymentStatus(serverDeploymentStatus)));
        assertThat(theVolumeNamed(TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME + "-volume").on(serverDeployment).getSecret()
                        .getSecretName(),
                is(TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME));
        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newEntandoApp, client, dbDeployment);
        verifyThatAllVolumesAreMapped(newEntandoApp, client, serverDeployment);
    }

    private void verifyTheServerContainer(Container theServerContainer) {
        //Exposing a port 8080
        assertThat(thePortNamed(SERVER_PORT).on(theServerContainer).getContainerPort(), is(8080));
        assertThat(thePortNamed(SERVER_PORT).on(theServerContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom  Entando image version specified
        assertThat(theServerContainer.getImage(), is("docker.io/entando/entando-keycloak:6.0.0-SNAPSHOT"));
        //And that is configured to point to the DB Service
        assertThat(theVariableReferenceNamed(KEYCLOAK_USER).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_APP_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(KEYCLOAK_USER).on(theServerContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(KEYCLOAK_PASSWORD).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_APP_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(KEYCLOAK_PASSWORD).on(theServerContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableNamed(DB_ADDR).on(theServerContainer),
                is(MY_APP_DB_SERVICE + "." + MY_APP_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DB_PORT_VAR).on(theServerContainer), is("3306"));
        assertThat(theVariableNamed(DB_DATABASE).on(theServerContainer), is(MY_APP_DATABASE));
        assertThat(theVariableReferenceNamed(DB_USER).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_APP_DB_SECRET));
        assertThat(theVariableReferenceNamed(DB_USER).on(theServerContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DB_PASSWORD).on(theServerContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableNamed(DB_VENDOR).on(theServerContainer), is("mysql"));
        assertThat(theVolumeMountNamed(TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME + "-volume").on(theServerContainer)
                        .getMountPath(),
                is(SecretCreator.CERT_SECRET_MOUNT_ROOT + "/" + TlsAware.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME));
    }

    private void verifyTheDbContainer(Container theDbContainer) {
        //Exposing a port 3306
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/mysql-80-centos7:latest"));
    }

    @Test
    void testPersistentVolumeClaims() {
        //Given I have  a Keycloak server
        EntandoApp newEntandoApp = this.entandoApp;
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
