package org.entando.kubernetes.controller.inprocesstest.legacy;

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
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SampleController;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.inprocesstest.FluentTraversals;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.externaldatabase.EntandoExternalDB;
import org.entando.kubernetes.model.externaldatabase.EntandoExternalDBSpec;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
//in execute component test
@Tag("in-process")
public class DeployExampleServiceOnExternalDatabaseTest implements InProcessTestUtil, FluentTraversals {

    public static final String MY_KEYCLOAK_SERVER_DEPLOYMENT = MY_KEYCLOAK + "-server-deployment";
    private static final String MY_KEYCLOAK_DB_SECRET = MY_KEYCLOAK + "-db-secret";
    private final EntandoKeycloakServer keycloakServer = newEntandoKeycloakServer();
    private final EntandoExternalDB externalDatabase = buildEntandoExternalDB();
    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    private SampleController sampleController;

    @BeforeEach
    public void setEntandoExternalDBNamespace() {
        this.sampleController = new SampleController<EntandoKeycloakServer>(client, keycloakClient) {
        };
        client.secrets().overwriteControllerSecret(buildKeycloakSecret());

        externalDatabase.getMetadata().setNamespace(keycloakServer.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, keycloakServer.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, keycloakServer.getMetadata().getName());
        client.entandoResources().putEntandoExternalDB(externalDatabase);
        client.entandoResources().putEntandoCustomResource(keycloakServer);
    }

    @Test
    public void testSecrets() {
        //Given I have created an EntandoExternalDB custom resource
        new CreateExternalServiceCommand(externalDatabase).execute(client);
        //When I deploy a EntandoKeycloakServer
        sampleController.onStartup(new StartupEvent());
        //Then a K8S Secret was created with a name that reflects the EntandoApp and the fact that it is a secret
        NamedArgumentCaptor<Secret> keycloakSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(keycloakServer), keycloakSecretCaptor.capture());
        Secret keycloakSecret = keycloakSecretCaptor.getValue();
        assertThat(keycloakSecret.getStringData().get(KubeUtils.USERNAME_KEY), is("my_keycloak_db"));
        assertThat(keycloakSecret.getStringData().get(KubeUtils.PASSSWORD_KEY), is(not(emptyOrNullString())));
    }

    @Test
    public void testDeployment() {
        //Given I have created an EntandoExternalDB custom resource
        new CreateExternalServiceCommand(externalDatabase).execute(client);
        //And Keycloak is receiving requests
        lenient().when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn(KEYCLOAK_SECRET);
        //When I deploy a EntandoKeycloakServer
        sampleController.onStartup(new StartupEvent());

        //Then a K8S deployment is created
        NamedArgumentCaptor<Deployment> keyclaokDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_KEYCLOAK_SERVER_DEPLOYMENT);
        verify(client.deployments()).createDeployment(eq(keycloakServer), keyclaokDeploymentCaptor.capture());
        //Then a pod was created for Keycloak using the credentials and connection settings of the EntandoExternalDB
        LabeledArgumentCaptor<Pod> keycloakSchemaJobCaptor = forResourceWithLabel(Pod.class, KEYCLOAK_SERVER_LABEL_NAME, MY_KEYCLOAK)
                .andWithLabel(KubeUtils.DB_JOB_LABEL_NAME, MY_KEYCLOAK + "-db-preparation-job");
        verify(client.pods()).runToCompletion(eq(keycloakServer), keycloakSchemaJobCaptor.capture());
        Pod keycloakDbJob = keycloakSchemaJobCaptor.getValue();
        Container theInitContainer = theInitContainerNamed(MY_KEYCLOAK + "-db-schema-creation-job").on(keycloakDbJob);
        verifyStandardSchemaCreationVariables("my-secret", MY_KEYCLOAK_DB_SECRET, theInitContainer, DbmsImageVendor.ORACLE);
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(theInitContainer),
                is("mydb-service." + MY_KEYCLOAK_NAMESPACE + ".svc.cluster.local"));
        //And it was instructed to create a schema reflecting the keycloakdb user
        assertThat(theVariableNamed(DATABASE_NAME).on(theInitContainer), is("my_db"));
    }

    private EntandoExternalDB buildEntandoExternalDB() {
        EntandoExternalDB edb = new EntandoExternalDB(
                new EntandoExternalDBSpec(DbmsImageVendor.ORACLE, "myoracle.com", 1521, "my_db", "my-secret"));
        edb.getMetadata().setName("mydb");
        edb.getMetadata().setNamespace("mynamespace");
        return edb;
    }
}