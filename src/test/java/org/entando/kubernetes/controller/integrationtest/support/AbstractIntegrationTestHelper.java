package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public class AbstractIntegrationTestHelper<
        R extends EntandoCustomResource,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>
        > {

    protected final DefaultKubernetesClient client;
    protected final CustomResourceOperationsImpl<R, L, D> operations;
    private final String domainSuffix;

    protected AbstractIntegrationTestHelper(DefaultKubernetesClient client, OperationsProducer<R, L, D> producer) {
        this.client = client;
        this.operations = producer.produce(client);
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

    public JobPodWaiter waitForJobPod(JobPodWaiter mutex, String namespace, String jobName) {
        waitFor(20).seconds().until(
                () -> client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems()
                        .size() > 0);
        Pod pod = client.pods().inNamespace(namespace).withLabel(KubeUtils.DB_JOB_LABEL_NAME, jobName).list().getItems().get(0);
        mutex.throwException(IllegalStateException.class)
                .waitOn(client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()));
        return mutex;
    }

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

    public void listen(String namespace, MainMethod mainMethod) {
        operations.inNamespace(namespace).watch(new Watcher<R>() {
            @Override
            public void eventReceived(Action action, R resource) {
                if (action == Action.ADDED) {
                    try {
                        System.out.println("!!!!!!!On " + resource.getKind() + " add!!!!!!!!!");
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, action.name());
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, resource.getMetadata().getNamespace());
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, resource.getMetadata().getName());
                        mainMethod.main(new String[0]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                cause.printStackTrace();
            }
        });
    }

    public interface MainMethod {

        void main(String[] args);
    }

    public interface OperationsProducer<R extends EntandoCustomResource, L extends CustomResourceList<R>,
            D extends DoneableEntandoCustomResource<D, R>> {

        CustomResourceOperationsImpl<R, L, D> produce(KubernetesClient client);
    }
}
