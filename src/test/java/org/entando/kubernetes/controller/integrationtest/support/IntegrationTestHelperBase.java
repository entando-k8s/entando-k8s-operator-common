package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.client.OperationsSupplier;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.controller.integrationtest.support.ControllerStartupEventFiringListener.OnStartupMethod;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public class IntegrationTestHelperBase<
        R extends EntandoCustomResource,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>
        > implements FluentIntegrationTesting {

    protected final DefaultKubernetesClient client;
    protected final CustomResourceOperationsImpl<R, L, D> operations;
    private final String domainSuffix;
    private final ControllerStartupEventFiringListener<R, L, D> startupEventFiringListener;
    private ControllerContainerStartingListener<R, L, D> containerStartingListener;

    protected IntegrationTestHelperBase(DefaultKubernetesClient client, OperationsSupplier<R, L, D> producer) {
        this.client = client;
        this.operations = producer.get(client);
        domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
        containerStartingListener = new ControllerContainerStartingListener<>(this.operations);
        startupEventFiringListener = new ControllerStartupEventFiringListener<>(getOperations());
    }

    protected static void logWarning(String x) {
        System.out.println(x);
    }

    public void afterTest() {
        startupEventFiringListener.stopListening();
        containerStartingListener.stopListening();
    }

    public CustomResourceOperationsImpl<R, L, D> getOperations() {
        return operations;
    }

    public void setTestFixture(TestFixtureRequest request) {
        TestFixturePreparation.prepareTestFixture(this.client, request);
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

    @SuppressWarnings("unchecked")
    public JobPodWaiter waitForJobPod(JobPodWaiter mutex, String namespace, String jobName) {
        await().atMost(45, TimeUnit.SECONDS).ignoreExceptions().until(
                () -> client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems()
                        .size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    @SuppressWarnings("unchecked")
    public ServicePodWaiter waitForServicePod(ServicePodWaiter mutex, String namespace, String deploymentName) {
        await().atMost(45, TimeUnit.SECONDS).ignoreExceptions().until(
                () -> client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                        .getItems().size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                .getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    public void listenAndRespondWithStartupEvent(String namespace, OnStartupMethod onStartupMethod) {
        startupEventFiringListener.listen(namespace, onStartupMethod);
    }

    public void listenAndRespondWithPod(String namespace, Optional<String> imageVersion) {
        String versionToUse = imageVersion.orElse(EntandoOperatorTestConfig.getVersionOfImageUnderTest().orElse("6.0.0-dev"));
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, versionToUse);
    }

    public void listenAndRespondWithImageVersionUnderTest(String namespace) {
        String versionToUse = EntandoOperatorTestConfig.getVersionOfImageUnderTest().orElseThrow(() -> new IllegalStateException(
                "The property 'entando.test.image.version' has not been set. Please set this property in your Maven command line"));
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, versionToUse);
    }

    @SuppressWarnings("unchecked")
    public void listenAndRespondWithLatestImage(String namespace) {
        String versionToUse = ControllerExecutor
                .resolveLatestImageFor(client, (Class<? extends EntandoBaseCustomResource>) operations.getType())
                .orElseThrow(() -> new IllegalStateException("No K8S Controller Image has been registered for " + operations.getType()));
        ControllerExecutor executor = new ControllerExecutor(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE, client);
        containerStartingListener.listen(namespace, executor, versionToUse);
    }

}

