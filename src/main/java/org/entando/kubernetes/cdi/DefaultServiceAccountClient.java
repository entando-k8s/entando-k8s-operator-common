package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultServiceAccountClient implements ServiceAccountClient {

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultServiceAccountClient(DefaultKubernetesClient client) {
        this.client = client;
    }

    @Override
    public ServiceAccount createServiceAccountIfAbsent(EntandoCustomResource peerInNamespace, ServiceAccount serviceAccount) {
        try {
            return client.serviceAccounts().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(serviceAccount);
        } catch (KubernetesClientException e) {
            return KubernetesExceptionProcessor.squashDuplicateExceptionOnCreate(peerInNamespace, serviceAccount, e);
        }
    }

    @Override
    public RoleBinding createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding) {
        try {
            return client.rbac().roleBindings().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(roleBinding);
        } catch (KubernetesClientException e) {
            return KubernetesExceptionProcessor.squashDuplicateExceptionOnCreate(peerInNamespace, roleBinding, e);
        }
    }

    @Override
    public Role createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role) {
        try {
            return client.rbac().roles().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(role);
        } catch (KubernetesClientException e) {
            return KubernetesExceptionProcessor.squashDuplicateExceptionOnCreate(peerInNamespace, role, e);
        }
    }
}
