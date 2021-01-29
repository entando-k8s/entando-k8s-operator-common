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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.DoneableServiceAccount;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.OperationInfo;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.sql.Timestamp;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.EntandoCustomResource;

/**
 * <p>RBAC objects are extremely sensitive resources in Kubernetes. Generally we expect customers to prohibiting us from
 * creating or modifying them, as this would allow our code to  create Roles that can perform any admin operation in a namespace. Even
 * though it is limited  to a namespace, the customer may have secrets or other objects that need to be protected.</p>
 *
 * <p>If the customer does indeed prevent us from creating RBAC objects (which is the default assumption) the customer
 * would have to use our  predefined roles from another source such as a Helm or Openshift template, or perhaps the Operator Framework.
 * However, in cases where we are allowed to create them programmatically, it is always simpler to let the code do it without any manual
 * intervention.</p>
 *
 * <p>This class will fail if the entando-operator ServiceAccount does not have GET access to ServiceAccounts, Roles
 * and RoleBindings. If the objects to be created don't already exist, this class will also fail if it doesn't have CREATE access to these
 * objects.</p>
 */
public class DefaultServiceAccountClient implements ServiceAccountClient {

    private final KubernetesClient client;

    public DefaultServiceAccountClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public DoneableServiceAccount findOrCreateServiceAccount(EntandoCustomResource peerInNamespace,
            String name) {
        try {
            Resource<ServiceAccount, DoneableServiceAccount> serviceAccountResource = client.serviceAccounts()
                    .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name);
            if (serviceAccountResource.get() == null) {
                return serviceAccountResource.createNew().withNewMetadata().withName(name).endMetadata();
            } else {
                return serviceAccountResource.edit().editMetadata()
                        //to ensure there is a state change so that the patch request does not get rejected
                        .addToAnnotations(KubeUtils.UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
                        .endMetadata();
            }
        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, "ServiceAccount", name);
        }
    }

    @Override
    public String createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding) {
        return createIfAbsent(peerInNamespace, roleBinding, client.rbac().roleBindings());
    }

    @Override
    public RoleBinding loadRoleBinding(EntandoCustomResource peerInNamespace, String name) {
        return load(peerInNamespace, name, client.rbac().roleBindings());
    }

    @Override
    public String createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role) {
        return createIfAbsent(peerInNamespace, role, client.rbac().roles());
    }

    @Override
    public Role loadRole(EntandoCustomResource peerInNamespace, String name) {
        return load(peerInNamespace, name, client.rbac().roles());
    }

    private <R extends HasMetadata, D extends Doneable<R>> String createIfAbsent(EntandoCustomResource peerInNamespace, R resource,
            MixedOperation<R, ?, D, Resource<R, D>> operation) {
        if (load(peerInNamespace, resource.getMetadata().getName(), operation) == null) {
            return create(peerInNamespace, resource, operation);
        }
        return resource.getMetadata().getName();
    }

    @SuppressWarnings("unchecked")
    private <R extends HasMetadata, D extends Doneable<R>> String create(EntandoCustomResource peerInNamespace, R resource,
            MixedOperation<R, ?, D, Resource<R, D>> operation) {
        try {
            return operation.inNamespace(peerInNamespace.getMetadata().getNamespace()).create(resource)
                    .getMetadata().getName();
        } catch (KubernetesClientException e) {
            return KubernetesExceptionProcessor.squashDuplicateExceptionOnCreate(peerInNamespace, resource, e);
        }
    }

    private <R extends HasMetadata, D extends Doneable<R>> R load(EntandoCustomResource peerInNamespace, String name,
            MixedOperation<R, ?, D, Resource<R, D>> operation) {
        try {
            return operation.inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, ((OperationInfo) operation).getKind(), name);
        }
    }

}
