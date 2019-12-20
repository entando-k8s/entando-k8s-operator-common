package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
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

public class ClusterInfrastructureIntegrationTestHelper extends IntegrationTestHelperBase<
        EntandoClusterInfrastructure,
        EntandoClusterInfrastructureList,
        DoneableEntandoClusterInfrastructure
        > {

    public static final String CLUSTER_INFRASTRUCTURE_NAMESPACE = EntandoOperatorTestConfig
            .calculateNameSpace("entando-infra-namespace");
    public static final String CLUSTER_INFRASTRUCTURE_NAME = EntandoOperatorTestConfig.calculateName("eti");

    ClusterInfrastructureIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoClusterInfrastructureOperationFactory::produceAllEntandoClusterInfrastructures);
    }

    public void ensureInfrastructureSecret() {
        delete(client.secrets())
                .named(EntandoOperatorConfig.getEntandoInfrastructureSecretName())
                .fromNamespace(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE)
                .waitingAtMost(20, SECONDS);
        String hostName = "http://" + CLUSTER_INFRASTRUCTURE_NAME + "." + getDomainSuffix();
        client.secrets()
                .inNamespace(TestFixturePreparation.ENTANDO_CONTROLLERS_NAMESPACE)
                .createNew()
                .withNewMetadata()
                .withName(EntandoOperatorConfig.getEntandoInfrastructureSecretName())
                .endMetadata()
                .addToStringData("entandoK8SServiceClientId", CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc")
                .addToStringData("entandoK8SServiceInternalUrl", hostName + "/k8s")
                .addToStringData("entandoK8SServiceExternalUrl", hostName + "/k8s")
                .addToStringData("entandoUserManagementClientId", CLUSTER_INFRASTRUCTURE_NAME + "-user-mgmt")
                .addToStringData("userManagementInternalUrl", hostName + "/user-mgmt")
                .addToStringData("userManagementExternalUrl", "/user-mgmt")
                .done();
    }

    boolean ensureClusterInfrastructure() {
        EntandoClusterInfrastructure infrastructure = getOperations()
                .inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME).get();
        if (infrastructure == null || infrastructure.getStatus().getEntandoDeploymentPhase() != EntandoDeploymentPhase.SUCCESSFUL) {
            setTestFixture(deleteAll(EntandoClusterInfrastructure.class).fromNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE));
            waitForClusterInfrastructure(
                    new EntandoClusterInfrastructureBuilder().withNewMetadata().withNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                            .withName(CLUSTER_INFRASTRUCTURE_NAME).endMetadata()
                            .withNewSpec().withDbms(DbmsImageVendor.POSTGRESQL).withDefault(true).withReplicas(1)
                            .withIngressHostName(CLUSTER_INFRASTRUCTURE_NAME + "." + getDomainSuffix()).endSpec().build(), 30, true);
            return true;
        }
        return false;
    }

    public void waitForClusterInfrastructure(EntandoClusterInfrastructure infrastructure, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE).create(infrastructure);
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
        await().atMost(30, SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                            .withName(CLUSTER_INFRASTRUCTURE_NAME)
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("user-mgmt").isPresent() && status.forServerQualifiedBy("k8s-svc").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

}
