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

package org.entando.kubernetes.controller.support.client.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.OperationInfo;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.net.HttpURLConnection;
import java.sql.Timestamp;
// FABRIC8 6.x: DoneableServiceAccount removed
// import org.entando.kubernetes.controller.support.client.DoneableServiceAccount;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

/**
 * <p>RBAC objects are extremely sensitive resources in Kubernetes. Generally we expect customers to prohibiting us
 * from
 * creating or modifying them, as this would allow our code to  create Roles that can perform any admin operation in a
 * namespace. Even
 * though it is limited  to a namespace, the customer may have secrets or other objects that need to be protected.</p>
 *
 * <p>If the customer does indeed prevent us from creating RBAC objects (which is the default assumption) the customer
 * would have to use our  predefined roles from another source such as a Helm or Openshift template, or perhaps the
 * Operator Framework.
 * However, in cases where we are allowed to create them programmatically, it is always simpler to let the code do it
 * without any manual
 * intervention.</p>
 *
 * <p>This class will fail if the entando-operator ServiceAccount does not have GET access to ServiceAccounts, Roles
 * and RoleBindings. If the objects to be created don't already exist, this class will also fail if it doesn't have
 * CREATE access to these
 * objects.</p>
 */
public class DefaultServiceAccountClient implements ServiceAccountClient {

    private static final String UPDATED_ANNOTATION_NAME = "entando.org/updated";//To avoid  http 400s
    private final KubernetesClient client;

    public DefaultServiceAccountClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public ServiceAccount findOrCreateServiceAccount(EntandoCustomResource peerInNamespace, String name) {
        // FABRIC8 6.x MIGRATION:
        // OLD CODE (Fabric8 5.x with DoneableServiceAccount):
        /*
        final Resource<ServiceAccount> as = client.serviceAccounts()
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name);
        try {
            createIfAbsent(peerInNamespace, new ServiceAccountBuilder()
                    .withNewMetadata()
                    .withNamespace(peerInNamespace.getMetadata().getNamespace())
                    .withName(name)
                    .endMetadata()
                    .build(), client.serviceAccounts());
            return new DoneableServiceAccount(as.fromServer().get(), as::patch).editMetadata()
                    //to ensure there is a state change so that the patch request does not get rejected
                    .addToAnnotations(UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
                    .endMetadata();

        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, "ServiceAccount", name);
        }
        */

        // NEW CODE (Fabric8 6.x):
        try {
            createIfAbsent(peerInNamespace, new ServiceAccountBuilder()
                    .withNewMetadata()
                    .withNamespace(peerInNamespace.getMetadata().getNamespace())
                    .withName(name)
                    .endMetadata()
                    .build(), client.serviceAccounts());

            // Return the service account from server
            return client.serviceAccounts()
                    .inNamespace(peerInNamespace.getMetadata().getNamespace())
                    .withName(name)
                    .get();

        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, "ServiceAccount", name);
        }
    }

    @Override
    public ServiceAccount updateServiceAccount(EntandoCustomResource peerInNamespace, ServiceAccount serviceAccount) {
        try {
            // FABRIC8 6.x MIGRATION:
            // OLD CODE
            // Add timestamp annotation to ensure state change (avoid HTTP 400)
            //ServiceAccount updated = new ServiceAccountBuilder(serviceAccount)
            //        .editOrNewMetadata()
            //        .addToAnnotations(UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
            //        .endMetadata()
            //        .build();

            // Get the latest version from the server to avoid resourceVersion conflicts
            ServiceAccount latest = client.serviceAccounts()
                    .inNamespace(peerInNamespace.getMetadata().getNamespace())
                    .withName(serviceAccount.getMetadata().getName())
                    .get();

            // Apply desired changes from serviceAccount parameter to the latest version
            ServiceAccount updated = new ServiceAccountBuilder(latest)
                    .editOrNewMetadata()
                        // Add timestamp annotation to ensure state change (avoid HTTP 400)
                        .addToAnnotations(UPDATED_ANNOTATION_NAME, new Timestamp(System.currentTimeMillis()).toString())
                        // Merge annotations from the desired serviceAccount
                        .addToAnnotations(serviceAccount.getMetadata().getAnnotations() != null
                                ? serviceAccount.getMetadata().getAnnotations()
                                : java.util.Collections.emptyMap())
                    .endMetadata()
                    // Apply other changes if needed (secrets, imagePullSecrets, etc.)
                    .withSecrets(serviceAccount.getSecrets())
                    .withImagePullSecrets(serviceAccount.getImagePullSecrets())
                    .build();

            return client.serviceAccounts()
                    .inNamespace(peerInNamespace.getMetadata().getNamespace())
                    .resource(updated)
                    .update();

        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, "ServiceAccount",
                            serviceAccount.getMetadata().getName());
        }
    }

    @Override
    public ServiceAccount findServiceAccount(EntandoCustomResource peerInNamespace, String name) {
        final Resource<ServiceAccount> serviceAccountResource = client.serviceAccounts()
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name);
        return serviceAccountResource.get();
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

    // FABRIC8 6.x MIGRATION:
    // OLD TYPE SIGNATURE (Fabric8 5.x):
    // private <R extends HasMetadata> String createIfAbsent(EntandoCustomResource peerInNamespace, R resource,
    //         MixedOperation<R, ?, Resource<R>> operation)

    // NEW TYPE SIGNATURE (Fabric8 6.x):
    // The third type parameter is no longer Resource<R>, it's now a specific resource type (e.g., ServiceAccountResource)
    // So we use a bounded wildcard
    @SuppressWarnings("unchecked")
    private <R extends HasMetadata> String createIfAbsent(EntandoCustomResource peerInNamespace, R resource,
            MixedOperation<R, ?, ?> operation) {
        try {
            // FABRIC8 6.x: Use resource() method instead of create(resource) directly
            R created = (R) operation.inNamespace(peerInNamespace.getMetadata().getNamespace())
                    .resource(resource)
                    .create();
            return created.getMetadata().getName();
        } catch (KubernetesClientException e) {
            if (e.getCode() != HttpURLConnection.HTTP_CONFLICT) {
                throw e;
            }
        }
        return resource.getMetadata().getName();
    }

    // FABRIC8 6.x MIGRATION:
    // OLD TYPE SIGNATURE (Fabric8 5.x):
    // private <R extends HasMetadata> R load(EntandoCustomResource peerInNamespace, String name,
    //         MixedOperation<R, ?, Resource<R>> operation)

    // NEW TYPE SIGNATURE (Fabric8 6.x):
    @SuppressWarnings("unchecked")
    private <R extends HasMetadata> R load(EntandoCustomResource peerInNamespace, String name,
            MixedOperation<R, ?, ?> operation) {
        try {
            return (R) operation.inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
        } catch (KubernetesClientException e) {
            throw KubernetesExceptionProcessor
                    .processExceptionOnLoad(peerInNamespace, e, ((OperationInfo) operation).getKind(), name);
        }
    }

}
