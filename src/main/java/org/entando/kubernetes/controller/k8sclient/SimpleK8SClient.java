package org.entando.kubernetes.controller.k8sclient;

public interface SimpleK8SClient<T extends EntandoResourceClient> {

    ServiceClient services();

    IngressClient ingresses();

    DeploymentClient deployments();

    PodClient pods();

    PersistentVolumeClaimClient persistentVolumeClaims();

    SecretClient secrets();

    T entandoResources();

    ServiceAccountClient serviceAccounts();

}
