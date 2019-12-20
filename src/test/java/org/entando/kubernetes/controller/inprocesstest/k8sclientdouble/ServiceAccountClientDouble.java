package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceAccountClientDouble extends AbstractK8SClientDouble implements ServiceAccountClient {

    public ServiceAccountClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public ServiceAccount createServiceAccountIfAbsent(EntandoCustomResource peerInNamespace, ServiceAccount serviceAccount) {
        getNamespace(peerInNamespace).putServiceAccount(serviceAccount);
        return serviceAccount;
    }

    @Override
    public RoleBinding createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding) {
        getNamespace(peerInNamespace).putRoleBinding(roleBinding);
        return roleBinding;
    }

    @Override
    public Role loadRole(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getRole(name);
    }

    @Override
    public RoleBinding loadRoleBinding(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getRoleBinding(name);
    }

    @Override
    public Role createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role) {
        getNamespace(peerInNamespace).putRole(role);
        return role;
    }

    @Override
    public ServiceAccount loadServiceAccount(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getServiceAccount(name);
    }
}
