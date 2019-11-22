package org.entando.kubernetes.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.k8sclient.PodClient;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;

@ApplicationScoped
public class DefaultSimpleK8SClient implements SimpleK8SClient<EntandoResourceClient> {

    @Inject
    private ServiceClient serviceClient;
    @Inject
    private PodClient podClient;
    @Inject
    private SecretClient secretClient;
    @Inject
    private EntandoResourceClient entandoResourceClient;
    @Inject
    private DeploymentClient deploymentClient;
    @Inject
    private IngressClient ingressClient;
    @Inject
    private PersistentVolumeClaimClient persistentVolumeClaimClient;
    @Inject
    private ServiceAccountClient serviceAccountClient;

    @Override
    public ServiceClient services() {
        return this.serviceClient;
    }

    @Override
    public PodClient pods() {
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        return this.secretClient;
    }

    @Override
    public EntandoResourceClient entandoResources() {
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        return this.persistentVolumeClaimClient;
    }

}
