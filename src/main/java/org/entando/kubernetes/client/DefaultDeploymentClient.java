package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient {

    private final KubernetesClient client;

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Deployment createDeployment(EntandoCustomResource peerInNamespace, Deployment deployment) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
