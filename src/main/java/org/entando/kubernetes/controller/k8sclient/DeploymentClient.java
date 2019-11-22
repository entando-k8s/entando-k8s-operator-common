package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface DeploymentClient {

    Deployment createDeployment(EntandoCustomResource customResource, Deployment deployment);

    Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name);
}
