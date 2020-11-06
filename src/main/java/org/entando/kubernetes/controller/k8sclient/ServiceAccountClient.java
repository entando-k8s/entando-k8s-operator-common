/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.DoneableServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public interface ServiceAccountClient {

    <T extends EntandoDeploymentSpec> String createRoleBindingIfAbsent(EntandoBaseCustomResource<T> peerInNamespace,
            RoleBinding roleBinding);

    <T extends EntandoDeploymentSpec> RoleBinding loadRoleBinding(EntandoBaseCustomResource<T> peerInNamespace, String name);

    <T extends EntandoDeploymentSpec> String createRoleIfAbsent(EntandoBaseCustomResource<T> peerInNamespace, Role role);

    <T extends EntandoDeploymentSpec> Role loadRole(EntandoBaseCustomResource<T> peerInNamespace, String name);

    <T extends EntandoDeploymentSpec> DoneableServiceAccount findOrCreateServiceAccount(EntandoBaseCustomResource<T> peerInNamespace,
            String name);
}
