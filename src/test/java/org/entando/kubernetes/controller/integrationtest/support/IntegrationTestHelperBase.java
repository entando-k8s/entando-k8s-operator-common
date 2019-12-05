package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.client.OperationsSupplier;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.controller.integrationtest.support.ControllerStartupEventFiringListener.OnStartupMethod;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public class IntegrationTestHelperBase<
        R extends EntandoCustomResource,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>
        > {

    protected final DefaultKubernetesClient client;
    protected final CustomResourceOperationsImpl<R, L, D> operations;
    private final String domainSuffix;

    protected IntegrationTestHelperBase(DefaultKubernetesClient client, OperationsSupplier<R, L, D> producer) {
        this.client = client;
        this.operations = producer.get(client);
        domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(client));
    }

    protected static void logWarning(String x) {
        System.out.println(x);
    }

    public CustomResourceOperationsImpl<R, L, D> getOperations() {
        return operations;
    }

    public void recreateNamespaces(String... namespaces) {
        IntegrationClientFactory.recreateNamespaces(this.client, namespaces);
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }

    @SuppressWarnings("unchecked")
    public JobPodWaiter waitForJobPod(JobPodWaiter mutex, String namespace, String jobName) {
        waitFor(20).seconds().until(
                () -> client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems()
                        .size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    @SuppressWarnings("unchecked")
    public ServicePodWaiter waitForServicePod(ServicePodWaiter mutex, String namespace, String deploymentName) {
        waitFor(20).seconds().until(
                () -> client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                        .getItems().size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(DeployCommand.DEPLOYMENT_LABEL_NAME, deploymentName).list()
                .getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

    public void listenAndRespondWithStartupEvent(String namespace, OnStartupMethod onStartupMethod) {
        new ControllerStartupEventFiringListener<>(getOperations()).listen(namespace, onStartupMethod);
    }

    public void listenAndRespondWithPod(String namespace) {
        new ControllerContainerStartingListener<>(getOperations())
                .listen(namespace, new TestControllerExecutor(IntegrationClientFactory.ENTANDO_CONTROLLERS_NAMESPACE, client));
    }

}

