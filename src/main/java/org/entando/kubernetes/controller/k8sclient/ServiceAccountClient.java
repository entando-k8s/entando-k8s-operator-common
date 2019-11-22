package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface ServiceAccountClient {

    ServiceAccount createServiceAccountIfAbsent(EntandoCustomResource peerInNamespace, ServiceAccount serviceAccount);

    RoleBinding createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding);

    Role createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role);

}
