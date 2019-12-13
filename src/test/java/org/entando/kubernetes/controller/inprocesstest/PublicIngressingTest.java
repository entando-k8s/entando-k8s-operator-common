package org.entando.kubernetes.controller.inprocesstest;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.PodResult.RUNNING_PHASE;
import static org.entando.kubernetes.controller.PodResult.SUCCEEDED_PHASE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.quarkus.runtime.StartupEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.entando.kubernetes.client.DefaultEntandoResourceClient;
import org.entando.kubernetes.client.DefaultPodClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.client.PodWatcherHolder;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SampleController;
import org.entando.kubernetes.controller.SampleDeployableContainer;
import org.entando.kubernetes.controller.SampleServerDeployable;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorE2ETestConfig;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginList;
import org.junit.Rule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.mockito.Mockito;

@Tag("in-process")
@EnableRuleMigrationSupport
/**
 * This is the new approach for testing this module. It uses the Kubernetes mock server. At this point in time, it seems to be a 50/50
 * decision whether to use the mock server or to mock the SimpleClient interfaces. Pros and cons are:
 * Pros:
 *    We can test most of the Client classes now
 *    We can trap issues with invalid or missing fields earlier
 *
 * Cons:
 *    The resulting tests are about 10 time slower than Mockito level mocking
 *    The mock server doesn't support Watches, so it was quite difficult to emulate the Websocket logic
 *    We still don't cover the Keycloak client
 * Future possibilities
 * We can perhaps implement test cases to run in one of three modes:
 * 1. Mockito mocked
 * 2. Mockserver
 * 3. Actual server
 */
public class PublicIngressingTest implements InProcessTestUtil, FluentTraversals, VariableReferenceAssertions {

    public static final String SAMPLE_NAMESPACE = EntandoOperatorE2ETestConfig.calculateNameSpace("sample-namespace");
    public static final String SAMPLE_NAME = EntandoOperatorE2ETestConfig.calculateName("sample-name");
    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private SampleController controller;
    private AtomicReference<PodWatcher> currentPodWatcher = new AtomicReference<>();
    PodWatcherHolder podWatcherHolder = new PodWatcherHolder() {
        @Override
        public void current(PodWatcher w) {
            currentPodWatcher.set(w);
        }
    };

    @Test
    public void testBasicDeployment() {
        DefaultPodClient.setPodWatcherHolder(podWatcherHolder);
        //Given I have a controller that processes EntandoPlugins
        SimpleKeycloakClient mock = Mockito.mock(SimpleKeycloakClient.class);
        controller = new SampleController<EntandoPlugin>(new DefaultSimpleK8SClient(server.getClient()), mock) {
            @Override
            protected Deployable<ServiceDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SampleServerDeployable<EntandoPlugin>(newEntandoPlugin, databaseServiceResult, keycloakConnectionConfig) {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected List<DeployableContainer> createContainers(EntandoPlugin entandoResource) {

                        return Arrays
                                .asList(new SampleDeployableContainer<>(entandoResource),
                                        new SampleDeployableContainer<EntandoPlugin>(entandoResource) {
                                            @Override
                                            public int getPort() {
                                                return 8081;
                                            }

                                            @Override
                                            public String getWebContextPath() {
                                                return "/otherauth";
                                            }
                                        });
                    }
                };
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        server.getClient().secrets().create(buildKeycloakSecret());
        //When I create a new EntandoPlugin
        EntandoPlugin resource = new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(SAMPLE_NAMESPACE)
                .withName(SAMPLE_NAME).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .withDbms(DbmsImageVendor.POSTGRESQL).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
        onAdd(resource);
        emulatePodWatcherBehaviour();

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
        {
            EntandoCustomResourceStatus status = getOperations().inNamespace(resource.getMetadata().getNamespace()).list().getItems().get(0)
                    .getStatus();
            return status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
        });
        //Then I expect two deployments. This is where we can put all the assertions
        Deployment serverDeployment = server.getClient().apps().deployments().inNamespace(SAMPLE_NAMESPACE)
                .withName(SAMPLE_NAME + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-deployment").get();
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(2));
        Deployment dbDeployment = server.getClient().apps().deployments().inNamespace(SAMPLE_NAMESPACE)
                .withName(SAMPLE_NAME + "-db-deployment").get();
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));
    }

    protected void emulatePodWatcherBehaviour() {
        await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
        currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithReadyStatus());
        await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
        currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithSucceededStatus());
        await().atMost(30, TimeUnit.SECONDS).until(() -> currentPodWatcher.get() != null);
        currentPodWatcher.getAndSet(null).eventReceived(Action.MODIFIED, podWithReadyStatus());
    }

    @SuppressWarnings("unchecked")
    protected CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin> getOperations() {
        return (CustomResourceOperationsImpl<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin>) DefaultEntandoResourceClient
                .getOperationsFor(server.getClient(), EntandoPlugin.class);
    }

    protected Pod podWithReadyStatus() {
        return podWithStatus(new PodStatusBuilder().withPhase(RUNNING_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build());
    }

    protected Pod podWithSucceededStatus() {
        return podWithStatus(new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build());
    }

    private Pod podWithStatus(PodStatus status) {
        return server.getClient().pods().inNamespace(SAMPLE_NAMESPACE).createNew().withNewMetadata()
                .withName(SAMPLE_NAME + "123")
                .withNamespace(SAMPLE_NAMESPACE).addToLabels(DEPLOYMENT_LABEL_NAME, SAMPLE_NAME + "-db").endMetadata()
                .editOrNewSpec().addNewContainer().endContainer().endSpec()
                .editOrNewSpec().addNewInitContainer().endInitContainer().endSpec()
                .withStatus(status).done();
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoBaseCustomResource> void onAdd(T resource) {
        new Thread(() -> {
            server.getClient().namespaces().createOrReplaceWithNew().withNewMetadata()
                    .withName(resource.getMetadata().getNamespace()).endMetadata().done();
            T createResource = (T) DefaultEntandoResourceClient.getOperationsFor(server.getClient(), resource.getClass())
                    .inNamespace(resource.getMetadata().getNamespace())
                    .create(resource);
            ((CustomResourceOperationsImpl<T, CustomResourceList <T>, Doneable<T>>)
            DefaultEntandoResourceClient.getOperationsFor(server.getClient(), resource.getClass())
                    .inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName())).edit();
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

}
