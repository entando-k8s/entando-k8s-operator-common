package org.entando.kubernetes.controller.integrationtest.support;

import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.time.Duration;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.infrastructure.DoneableEntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureList;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureOperationFactory;

public class ClusterInfrastructureIntegrationTestHelper extends AbstractIntegrationTestHelper {

    public static final String CLUSTER_INFRASTRUCTURE_NAME = "eti";
    public static final String CLUSTER_INFRASTRUCTURE_NAMESPACE = "entando-infra-namespace";
    private CustomResourceOperationsImpl<EntandoClusterInfrastructure, EntandoClusterInfrastructureList,
            DoneableEntandoClusterInfrastructure> clusterInfrastructureOperations;

    public ClusterInfrastructureIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client);
    }

    public boolean ensureClusterInfrastructure() {
        EntandoClusterInfrastructure infrastructure = getClusterInfrastructureOperations()
                .inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME).get();
        if (infrastructure == null || infrastructure.getStatus().getEntandoDeploymentPhase() != EntandoDeploymentPhase.SUCCESSFUL) {
            recreateNamespaces(CLUSTER_INFRASTRUCTURE_NAMESPACE);
            waitForClusterInfrastructure(
                    new EntandoClusterInfrastructureBuilder().withNewMetadata().withNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                            .withName(CLUSTER_INFRASTRUCTURE_NAME).endMetadata()
                            .withNewSpec().withDbms(DbmsImageVendor.POSTGRESQL).withDefault(true).withReplicas(1)
                            .withIngressHostName(CLUSTER_INFRASTRUCTURE_NAME + "." + getDomainSuffix()).endSpec().build(), 30, true);
            return true;
        }
        return false;
    }

    public void waitForClusterInfrastructure(EntandoClusterInfrastructure entandoClusterInfrastructure, int waitOffset,
            boolean deployingDbContainers) {
        getClusterInfrastructureOperations().inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE).create(entandoClusterInfrastructure);
        if (deployingDbContainers) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    CLUSTER_INFRASTRUCTURE_NAMESPACE, CLUSTER_INFRASTRUCTURE_NAME + "-digexdb");
        }
        this.waitForJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(240 + waitOffset)),
                CLUSTER_INFRASTRUCTURE_NAMESPACE, CLUSTER_INFRASTRUCTURE_NAME + "-db-preparation-job");
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(240 + waitOffset)),
                CLUSTER_INFRASTRUCTURE_NAMESPACE, CLUSTER_INFRASTRUCTURE_NAME + "-dig-ex");
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                CLUSTER_INFRASTRUCTURE_NAMESPACE, CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc");
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(90)), CLUSTER_INFRASTRUCTURE_NAMESPACE,
                CLUSTER_INFRASTRUCTURE_NAME + "-user-mgmt");
        waitFor(30).seconds().orUntil(
                () -> {
                    EntandoCustomResourceStatus status = getClusterInfrastructureOperations()
                            .inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                            .withName(CLUSTER_INFRASTRUCTURE_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("user-mgmt").isPresent() && status.forServerQualifiedBy("k8s-svc").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

    public CustomResourceOperationsImpl<EntandoClusterInfrastructure, EntandoClusterInfrastructureList,
            DoneableEntandoClusterInfrastructure> getClusterInfrastructureOperations() {
        if (clusterInfrastructureOperations == null) {
            clusterInfrastructureOperations = EntandoClusterInfrastructureOperationFactory.produceAllEntandoClusterInfrastructures(client);
        }
        return clusterInfrastructureOperations;
    }

}
