package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultDeploymentClient implements DeploymentClient {

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultDeploymentClient(DefaultKubernetesClient client) {
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
