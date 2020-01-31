package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.KubeUtils.standardIngressName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.examples.SampleController;
import org.entando.kubernetes.controller.common.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.common.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.creators.KeycloakClientCreator;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.controller.test.support.FluentTraversals;
import org.entando.kubernetes.controller.test.support.PodBehavior;
import org.entando.kubernetes.controller.test.support.VariableReferenceAssertions;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public abstract class SpringBootContainerTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior, VariableReferenceAssertions {

    public static final String SAMPLE_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("sample-namespace");
    public static final String SAMPLE_NAME = EntandoOperatorTestConfig.calculateName("sample-name");
    public static final String SAMPLE_NAME_DB = KubeUtils.snakeCaseOf(SAMPLE_NAME + "_db");
    EntandoPlugin plugin1 = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    private SampleController controller;

    @Test
    public void testBasicDeployment() {
        //Given I have a controller that processes EntandoPlugins
        controller = new SampleController<EntandoPlugin>(getClient(), getKeycloakClient()) {
            @Override
            protected Deployable<ServiceDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SpringBootDeployable<>(newEntandoPlugin, keycloakConnectionConfig, databaseServiceResult);
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        getClient().secrets().overwriteControllerSecret(buildKeycloakSecret());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                getClient().entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a server deployment
        Deployment serverDeployment = getClient().deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), Matchers.is(1));
        verifyThatAllVariablesAreMapped(plugin1, getClient(), serverDeployment);
        verifyThatAllVolumesAreMapped(plugin1, getClient(), serverDeployment);
        verifySpringDatasource(serverDeployment);
        verifySpringOidc(serverDeployment);
        //Then  a db deployment
        Deployment dbDeployment = getClient().deployments().loadDeployment(plugin1, SAMPLE_NAME + "-db-deployment");
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));

        //And I an ingress paths
        Ingress ingress = getClient().ingresses().loadIngress(plugin1.getMetadata().getNamespace(), standardIngressName(plugin1));
        assertThat(theHttpPath(SampleSpringBootDeployableContainer.MY_WEB_CONTEXT).on(ingress).getBackend().getServicePort().getIntVal(),
                Matchers.is(8080));
    }

    protected abstract SimpleKeycloakClient getKeycloakClient();

    public void verifySpringOidc(Deployment serverDeployment) {
        assertThat(theVariableNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name())
                .on(thePrimaryContainerOn(serverDeployment)), is(MY_KEYCLOAK_BASE_URL + "/realms/entando"));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                        .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(),
                is(SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-secret"));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KeycloakClientCreator.CLIENT_ID_KEY));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                        .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(),
                is(SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-secret"));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH_2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KeycloakClientCreator.CLIENT_SECRET_KEY));
    }

    public void verifySpringDatasource(Deployment serverDeployment) {
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KubeUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(KubeUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_URL.name())
                .on(thePrimaryContainerOn(serverDeployment)), is(
                "jdbc:postgresql://" + SAMPLE_NAME + "-db-service." + SAMPLE_NAMESPACE + ".svc.cluster.local:5432/" + SAMPLE_NAME_DB));
    }

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .withDbms(DbmsImageVendor.POSTGRESQL).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
    }

    protected final void emulatePodWaitingBehaviour(EntandoCustomResource resource, String deploymentName) {
        new Thread(() -> {
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Deployment dbDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-db-deployment");
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithReadyStatus(dbDeployment));
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                String dbJobName = String.format("%s-db-preparation-job", resource.getMetadata().getName());
                Pod dbPreparationPod = getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), KubeUtils.DB_JOB_LABEL_NAME, dbJobName);
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithSucceededStatus(dbPreparationPod));
                await().atMost(10, TimeUnit.SECONDS).until(() -> getClient().pods().getPodWatcherHolder().get() != null);
                Deployment serverDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-server-deployment");
                getClient().pods().getPodWatcherHolder().getAndSet(null)
                        .eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoBaseCustomResource> void onAdd(T resource) {
        new Thread(() -> {
            T createResource = getClient().entandoResources().putEntandoCustomResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

}
