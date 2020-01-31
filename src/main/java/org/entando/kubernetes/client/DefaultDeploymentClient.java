package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient {

    private final KubernetesClient client;

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace, Deployment deployment) {
        RollableScalableResource<Deployment, DoneableDeployment> resource = client.apps()
                .deployments().inNamespace(peerInNamespace.getMetadata().getName()).withName(deployment.getMetadata().getName());
        if (resource.get() == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            resource.scale(0, true);
            resource.patch(deployment);
            return resource.scale(deployment.getSpec().getReplicas(), true);
        }
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
