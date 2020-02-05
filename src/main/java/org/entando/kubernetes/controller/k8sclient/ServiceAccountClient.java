package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface ServiceAccountClient {

    String createServiceAccountIfAbsent(EntandoCustomResource peerInNamespace, ServiceAccount serviceAccount);

    ServiceAccount loadServiceAccount(EntandoCustomResource peerInNamespace, String name);

    String createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding);

    RoleBinding loadRoleBinding(EntandoCustomResource peerInNamespace, String name);

    String createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role);

    Role loadRole(EntandoCustomResource peerInNamespace, String name);
}
