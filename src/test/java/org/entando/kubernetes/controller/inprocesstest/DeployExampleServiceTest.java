package org.entando.kubernetes.controller.inprocesstest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Map;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.example.KeycloakServerController;
import org.entando.kubernetes.controller.creators.DeploymentCreator;
import org.entando.kubernetes.controller.impl.TlsHelper;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
//in execute component test
@Tag("in-process")
public class DeployExampleServiceTest implements InProcessTestUtil, FluentTraversals {

    private static final String MY_KEYCLOAK_ADMIN_SECRET = MY_KEYCLOAK + "-admin-secret";
    private static final String MY_KEYCLOAK_SERVER = MY_KEYCLOAK + "-server";
    private static final String MY_KEYCLOAK_SERVER_SERVICE = MY_KEYCLOAK_SERVER + "-service";
    private static final String MY_KEYCLOAK_SERVER_DEPLOYMENT = MY_KEYCLOAK_SERVER + "-deployment";
    private static final String MY_KEYCLOAK_DB = MY_KEYCLOAK + "-db";
    private static final String MY_KEYCLOAK_DB_SERVICE = MY_KEYCLOAK_DB + "-service";
    private static final String MY_KEYCLOAK_DB_PVC = MY_KEYCLOAK_DB + "-pvc";
    private static final String MY_KEYCLOAK_DB_DEPLOYMENT = MY_KEYCLOAK_DB + "-deployment";
    private static final String MY_KEYCLOAK_DB_SECRET = MY_KEYCLOAK_DB + "-secret";
    private static final String MY_KEYCLOAK_INGRESS = MY_KEYCLOAK + "-" + KubeUtils.DEFAULT_INGRESS_SUFFIX;
    private static final String MY_KEYCLOAK_DB_CONTAINER = MY_KEYCLOAK_DB + "-container";
    private static final String MY_KEYCLOAK_DB_ADMIN_SECRET = MY_KEYCLOAK_DB + "-admin-secret";
    private static final String MY_KEYCLOAK_SERVER_CONTAINER = MY_KEYCLOAK_SERVER + "-container";
    private static final String DB_ADDR = "DB_ADDR";
    private static final String DB_PORT_VAR = "DB_PORT";
    private static final String DB_DATABASE = "DB_DATABASE";
    private static final String DB_USER = "DB_USER";
    private static final String DB_PASSWORD = "DB_PASSWORD";
    private static final String MY_KEYCLOAK_DATABASE = "my_keycloak_db";
    private static final String AUTH = "/auth";
    private final KeycloakServer keycloakServer = newKeycloakServer();
    @Spy
    private final SimpleK8SClient client = new SimpleK8SClientDouble();
    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    //Because PMD does know this is actually injected into subsequent fields
    private SimpleKeycloakClient keycloakClient;
    @InjectMocks
    private KeycloakServerController keycloakServerController;

    @AfterEach
    public void resetSystemProps() {
        System.getProperties().remove(EntandoOperatorConfig.ENTANDO_CA_CERT_PATHS);
        System.getProperties().remove(EntandoOperatorConfig.ENTANDO_PATH_TO_TLS_KEYPAIR);
        TlsHelper.getInstance().init();
    }

    @Test
    public void testSecrets() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        //Given I have an KeycloakServer custom resource with MySQL as database
        final KeycloakServer newKeycloakServer = keycloakServer;
        //And the trust cert has been configured correctly
        System.setProperty(EntandoOperatorConfig.ENTANDO_CA_CERT_PATHS,
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net", "ca.crt").normalize().toAbsolutePath().toString());
        //And the default TLS Keypair has been configured correctly
        System.setProperty(EntandoOperatorConfig.ENTANDO_PATH_TO_TLS_KEYPAIR,
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net").normalize().toAbsolutePath().toString());
        TlsHelper.getInstance().init();
        // WHen I have deploya the KeycloakServer
        keycloakServerController.onKeycloakServerAddition(newKeycloakServer);

        //Then a K8S Secret was created with a name that reflects the KeycloakServer and the fact that it is an admin secret
        NamedArgumentCaptor<Secret> adminSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_DB_ADMIN_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newKeycloakServer), adminSecretCaptor.capture());
        Secret theDbAdminSecret = adminSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(theDbAdminSecret), is("root"));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(theDbAdminSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(theDbAdminSecret), is(MY_KEYCLOAK));

        //And a K8S Secret was created with a name that reflects the KeycloakServer and the fact that it is the keycloakd db secret
        NamedArgumentCaptor<Secret> keycloakDbSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newKeycloakServer), keycloakDbSecretCaptor.capture());
        Secret keycloakDbSecret = keycloakDbSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(keycloakDbSecret), is(MY_KEYCLOAK_DATABASE));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(keycloakDbSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(keycloakDbSecret), is(MY_KEYCLOAK));

        //And a K8S Secret was created in the Keycloak deployment's namespace with a name that reflects the EntandoPlugin and the fact
        // that it is Keycloak admin secret
        NamedArgumentCaptor<Secret> keycloakAdminSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_ADMIN_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newKeycloakServer), keycloakAdminSecretCaptor.capture());
        Secret keycloakAdminSecret = keycloakAdminSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(keycloakAdminSecret), is(MY_KEYCLOAK_ADMIN_USERNAME));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(keycloakAdminSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(keycloakAdminSecret), is(MY_KEYCLOAK));

        //And a K8S Secret was created in the Keycloak deployment's namespace containing the CA keystore
        NamedArgumentCaptor<Secret> tlsSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK + "-tls-secret");
        verify(client.secrets(), atLeast(1)).createSecretIfAbsent(eq(newKeycloakServer), tlsSecretCaptor.capture());
        Secret tlsSecret = tlsSecretCaptor.getValue();
        assertThat(theKey(TlsHelper.TLS_KEY).on(tlsSecret), is(TlsHelper.getInstance().getTlsKeyBase64()));
        assertThat(theKey(TlsHelper.TLS_CRT).on(tlsSecret), is(TlsHelper.getInstance().getTlsCertBase64()));

        //And a K8S Secret was created in the Keycloak deployment's namespace containing the CA keystore
        NamedArgumentCaptor<Secret> trustStoreSecretCaptor = forResourceNamed(Secret.class,
                DeploymentCreator.DEFAULT_TRUST_STORE_SECRET_NAME);
        verify(client.secrets(), atLeast(1)).createSecretIfAbsent(eq(newKeycloakServer), trustStoreSecretCaptor.capture());
        Secret trustStoreSecret = trustStoreSecretCaptor.getValue();
        assertThat(theKey(DeploymentCreator.TRUST_STORE_FILE).on(trustStoreSecret), is(TlsHelper.getInstance().getTrustStoreBase64()));

        byte[] decode = Base64.getDecoder().decode(trustStoreSecret.getData().get(DeploymentCreator.TRUST_STORE_FILE).getBytes());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(new ByteArrayInputStream(decode), TlsHelper.getInstance().getTrustStorePassword().toCharArray());
        assertNotNull(ks.getCertificate("ca.crt"));

        //And a K8S Secret was created in the controllers' namespace with a name that reflects the fact that it is the default Keycloak
        // Admin Secret
        NamedArgumentCaptor<Secret> localKeycloakAdminSecretCaptor = forResourceNamed(Secret.class, DEFAULT_KEYCLOAK_ADMIN_SECRET);
        verify(client.secrets()).overwriteControllerSecret(localKeycloakAdminSecretCaptor.capture());
        Secret localKeycloakAdminSecret = localKeycloakAdminSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(localKeycloakAdminSecret), is(MY_KEYCLOAK_ADMIN_USERNAME));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(localKeycloakAdminSecret), is(not(emptyOrNullString())));
    }

    @Test
    public void testService() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;
        //And that K8S is up and receiving Service requests
        ServiceStatus dbServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_SERVICE)))
                .then(respondWithServiceStatus(dbServiceStatus));
        ServiceStatus javaServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_SERVER_SERVICE)))
                .then(respondWithServiceStatus(javaServiceStatus));

        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        keycloakServerController.onKeycloakServerAddition(newKeycloakServer);
        //Then a K8S Service was created with a name that reflects the EntandoApp and the fact that it is a JEE service
        NamedArgumentCaptor<Service> dbServiceCaptor = forResourceNamed(Service.class, MY_KEYCLOAK_DB_SERVICE);
        verify(client.services()).createService(eq(newKeycloakServer), dbServiceCaptor.capture());
        NamedArgumentCaptor<Service> serverServiceCaptor = forResourceNamed(Service.class, MY_KEYCLOAK_SERVER_SERVICE);
        verify(client.services()).createService(eq(newKeycloakServer), serverServiceCaptor.capture());
        //And a selector that matches the KeycloakServer pod
        Service serverService = serverServiceCaptor.getValue();
        Map<String, String> serverSelector = serverService.getSpec().getSelector();
        assertThat(serverSelector.get(DEPLOYMENT_LABEL_NAME), is(MY_KEYCLOAK_SERVER));
        assertThat(serverSelector.get(KEYCLOAK_SERVER_LABEL_NAME), is(MY_KEYCLOAK));
        //And the TCP port 8080 named 'server-port'
        assertThat(thePortNamed(SERVER_PORT).on(serverService).getPort(), is(8080));
        assertThat(thePortNamed(SERVER_PORT).on(serverService).getProtocol(), is(TCP));
        //And a selector that matches the Keyclaok DB pod
        Service dbService = dbServiceCaptor.getValue();
        Map<String, String> dbSelector = dbService.getSpec().getSelector();
        assertThat(dbSelector.get(DEPLOYMENT_LABEL_NAME), is(MY_KEYCLOAK_DB));
        assertThat(dbSelector.get(KEYCLOAK_SERVER_LABEL_NAME), is(MY_KEYCLOAK));
        //And the TCP port 3306 named 'db-port'
        assertThat(thePortNamed(DB_PORT).on(dbService).getPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(dbService).getProtocol(), is(TCP));
        //And the state of the two services was reloaded from K8S
        verify(client.services()).loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_SERVICE));
        verify(client.services()).loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_SERVER_SERVICE));
        //And K8S was instructed to update the status of the EntandoApp with the status of the java service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesServiceStatus(javaServiceStatus)));
        //And the db service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesServiceStatus(dbServiceStatus)));
    }

    @Test
    public void testIngress() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;
        //And that K8S is up and receiving Ingress requests
        IngressStatus ingressStatus = new IngressStatus();

        when(client.ingresses().loadIngress(eq(newKeycloakServer.getMetadata().getNamespace()), any(String.class)))
                .thenAnswer(respondWithIngressStatusForPath(ingressStatus, AUTH));

        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        keycloakServerController.onKeycloakServerAddition(newKeycloakServer);
        // Then a K8S Ingress Path was created with a name that reflects the name of the EntandoApp and
        // the fact that it is a the Keycloak path
        NamedArgumentCaptor<Ingress> ingressArgumentCaptor = forResourceNamed(Ingress.class, MY_KEYCLOAK_INGRESS);
        verify(client.ingresses()).createIngress(eq(newKeycloakServer), ingressArgumentCaptor.capture());
        Ingress resultingIngress = ingressArgumentCaptor.getValue();
        //With a path that reflects webcontext of Keycloak, mapped to the previously created service
        assertThat(theBackendFor(AUTH).on(resultingIngress).getServicePort().getIntVal(), is(8080));
        assertThat(theBackendFor(AUTH).on(resultingIngress).getServiceName(), is(MY_KEYCLOAK_SERVER_SERVICE));
        //And the Ingress state was reloaded from K8S
        verify(client.ingresses(), times(2))
                .loadIngress(eq(newKeycloakServer.getMetadata().getNamespace()), eq(MY_KEYCLOAK_INGRESS));

        //And K8S was instructed to update the status of the EntandoApp with the status of the ingress
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesIngressStatus(ingressStatus)));
    }

    @Test
    public void testSchemaPreparation() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;

        //When the DeployCommand processes the addition request
        keycloakServerController.onKeycloakServerAddition(keycloakServer);
        // A DB preparation Pod is created with labels linking it to the KeycloakServer
        LabeledArgumentCaptor<Pod> podCaptor = forResourceWithLabel(Pod.class, KEYCLOAK_SERVER_LABEL_NAME, MY_KEYCLOAK)
                //And the fact that it is a DB JOB
                .andWithLabel(KubeUtils.DB_JOB_LABEL_NAME, MY_KEYCLOAK_DB + "-preparation-job");
        verify(client.pods()).runToCompletion(eq(newKeycloakServer), podCaptor.capture());
        Pod theDbJobPod = podCaptor.getValue();
        //With exactly 1 container
        assertThat(theDbJobPod.getSpec().getInitContainers().size(), is(1));
        //And the DB Schema Preparation Container is configured with the appropriate Environment Variables
        Container theSchemaPeparationContainer = theInitContainerNamed(MY_KEYCLOAK_DB + "-schema-creation-job").on(theDbJobPod);
        assertThat(theVariableNamed(DATABASE_SCHEMA_COMMAND).on(theSchemaPeparationContainer), is("CREATE_SCHEMA"));
        assertThat(theVariableNamed(DATABASE_NAME).on(theSchemaPeparationContainer), is(MY_KEYCLOAK_DATABASE));
        assertThat(theVariableNamed(DATABASE_VENDOR).on(theSchemaPeparationContainer), is("mysql"));
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(theSchemaPeparationContainer),
                is(MY_KEYCLOAK_DB_SERVICE + "." + MY_KEYCLOAK_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DATABASE_SERVER_PORT).on(theSchemaPeparationContainer), is("3306"));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_DB_ADMIN_SECRET));
        assertThat(
                theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_DB_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_DB_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_DB_SECRET));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(theSchemaPeparationContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.PASSSWORD_KEY));
    }

    @Test
    public void testDeployment() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;
        //And the trust cert has been configured correctly
        System.setProperty(EntandoOperatorConfig.ENTANDO_CA_CERT_PATHS,
                Paths.get("src", "test", "resources", "tls", "ampie.dynu.net", "ca.crt").normalize().toAbsolutePath().toString());
        TlsHelper.getInstance().init();
        //And K8S is receiving Deployment requests
        DeploymentStatus serverDeploymentStatus = new DeploymentStatus();
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_SERVER_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(serverDeploymentStatus));

        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        keycloakServerController.onKeycloakServerAddition(newKeycloakServer);

        //Then two K8S deployments are created with a name that reflects the KeycloakServer name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_KEYCLOAK_DB_DEPLOYMENT);
        verify(client.deployments()).createDeployment(eq(newKeycloakServer), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        verifyTheDbContainer(theContainerNamed(MY_KEYCLOAK_DB_CONTAINER).on(dbDeployment));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK_DB));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()),
                is(MY_KEYCLOAK));

        // And a ServerDeployment
        NamedArgumentCaptor<Deployment> serverDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_KEYCLOAK_SERVER_DEPLOYMENT);
        verify(client.deployments()).createDeployment(eq(newKeycloakServer), serverDeploymentCaptor.capture());
        Deployment serverDeployment = serverDeploymentCaptor.getValue();
        //With a Pod Template that has labels linking it to the previously created K8S  Keycloak Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(serverDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK_SERVER));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(serverDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK));
        verifyTheServerContainer(theContainerNamed(MY_KEYCLOAK_SERVER_CONTAINER).on(serverDeployment));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT));
        verify(client.deployments()).loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_SERVER_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesDeploymentStatus(dbDeploymentStatus)));
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesDeploymentStatus(serverDeploymentStatus)));
        assertThat(theVolumeNamed(DeploymentCreator.DEFAULT_TRUST_STORE_SECRET_NAME + "-volume").on(serverDeployment).getSecret()
                        .getSecretName(),
                is(DeploymentCreator.DEFAULT_TRUST_STORE_SECRET_NAME));
        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newKeycloakServer, client, dbDeployment);
        verifyThatAllVolumesAreMapped(newKeycloakServer, client, serverDeployment);
    }

    private void verifyTheServerContainer(Container theServerContainer) {
        //Exposing a port 8080
        assertThat(thePortNamed(SERVER_PORT).on(theServerContainer).getContainerPort(), is(8080));
        assertThat(thePortNamed(SERVER_PORT).on(theServerContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theServerContainer.getImage(), is("docker.io/entando/entando-keycloak:6.0.0"));
        //And that is configured to point to the DB Service
        assertThat(theVariableReferenceNamed(KEYCLOAK_USER).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(KEYCLOAK_USER).on(theServerContainer).getSecretKeyRef().getKey(), is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(KEYCLOAK_PASSWORD).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_ADMIN_SECRET));
        assertThat(theVariableReferenceNamed(KEYCLOAK_PASSWORD).on(theServerContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.PASSSWORD_KEY));
        assertThat(theVariableNamed(DB_ADDR).on(theServerContainer),
                is(MY_KEYCLOAK_DB_SERVICE + "." + MY_KEYCLOAK_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DB_PORT_VAR).on(theServerContainer), is("3306"));
        assertThat(theVariableNamed(DB_DATABASE).on(theServerContainer), is("my_keycloak_db"));
        assertThat(theVariableReferenceNamed(DB_USER).on(theServerContainer).getSecretKeyRef().getName(),
                is(MY_KEYCLOAK_DB_SECRET));
        assertThat(theVariableReferenceNamed(DB_USER).on(theServerContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DB_PASSWORD).on(theServerContainer).getSecretKeyRef().getKey(),
                is(KubeUtils.PASSSWORD_KEY));
        assertThat(theVariableNamed(DB_VENDOR).on(theServerContainer), is("mysql"));
        assertThat(theVolumeMountNamed(DeploymentCreator.DEFAULT_TRUST_STORE_SECRET_NAME + "-volume").on(theServerContainer).getMountPath(),
                is("/etc/entando/keystores/entando-default-trust-store-secret"));
        assertThat(theVariableNamed("X509_CA_BUNDLE").on(theServerContainer),
                containsString("/etc/entando/keystores/entando-default-trust-store-secret/ca.crt"));
    }

    private void verifyTheDbContainer(Container theDbContainer) {
        //Exposing a port 3306
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/mysql-57-centos7:latest"));
    }

    @Test
    public void testPersistentVolumeClaims() {
        //Given I have  a Keycloak server
        KeycloakServer newKeycloakServer = this.keycloakServer;
        //And that K8S is up and receiving PVC requests
        PersistentVolumeClaimStatus dbPvcStatus = new PersistentVolumeClaimStatus();
        lenient().when(client.persistentVolumeClaims()
                .loadPersistentVolumeClaim(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_PVC)))
                .then(respondWithPersistentVolumeClaimStatus(dbPvcStatus));

        //When the KeycloakController is notified that a new KeycloakServer has been added
        keycloakServerController.onKeycloakServerAddition(newKeycloakServer);

        //Then K8S was instructed to create a PersistentVolumeClaim for the DB and the JEE Server
        NamedArgumentCaptor<PersistentVolumeClaim> dbPvcCaptor = forResourceNamed(PersistentVolumeClaim.class,
                MY_KEYCLOAK_DB_PVC);
        verify(this.client.persistentVolumeClaims())
                .createPersistentVolumeClaim(eq(newKeycloakServer), dbPvcCaptor.capture());
        //With names that reflect the EntandoPlugin and the type of deployment the claim is used for
        PersistentVolumeClaim dbPvc = dbPvcCaptor.getValue();

        //And labels that link this PVC to the EntandoApp, the EntandoPlugin and the specific deployment
        assertThat(dbPvc.getMetadata().getLabels().get(KEYCLOAK_SERVER_LABEL_NAME), is(MY_KEYCLOAK));
        assertThat(dbPvc.getMetadata().getLabels().get(DEPLOYMENT_LABEL_NAME), is(MY_KEYCLOAK_DB));

        //And both PersistentVolumeClaims were reloaded from  K8S for its latest state
        verify(this.client.persistentVolumeClaims())
                .loadPersistentVolumeClaim(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_PVC));

        // And K8S was instructed to update the status of the EntandoPlugin with
        // the status of both PersistentVolumeClaims
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(containsThePersistentVolumeClaimStatus(dbPvcStatus)));
    }

}
