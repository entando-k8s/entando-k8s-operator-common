package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DeploymentClientDouble extends AbstractK8SClientDouble implements DeploymentClient {

    public DeploymentClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Deployment createDeployment(EntandoCustomResource peerInNamespace, Deployment deployment) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putDeployment(deployment.getMetadata().getName(), deployment);
        Pod pod = createPodFrom(deployment);
        getNamespace(peerInNamespace).putPod(pod);
        return deployment;
    }

    private Pod createPodFrom(Deployment deployment) {
        return new PodBuilder().withNewMetadataLike(deployment.getMetadata()).endMetadata().build();
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getDeployment(name);
    }

}
