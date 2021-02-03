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

package org.entando.kubernetes.controller.support.creators;

import io.fabric8.kubernetes.api.model.DoneableServiceAccount;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KubernetesPermission;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.SecurityMode;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceAccountCreator extends AbstractK8SResourceCreator {

    public static final String ROLEBINDING_SUFFIX = "-rolebinding";
    private String role;

    public ServiceAccountCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public void prepareServiceAccountAccess(ServiceAccountClient serviceAccountClient, Deployable<?> deployable) {
        prepareServiceAccount(serviceAccountClient, deployable);
        if (EntandoOperatorConfig.getOperatorSecurityMode() == SecurityMode.LENIENT) {
            //TODO reevaluate this. Maybe we should just always create/sync the role. This is fairly safe as the operator will not
            //be able to create a role that has more access than its own permissions
            this.role = serviceAccountClient.createRoleIfAbsent(entandoCustomResource, newRole(deployable));
            serviceAccountClient.createRoleBindingIfAbsent(entandoCustomResource, newRoleBinding(deployable));
        }
        if (EntandoOperatorConfig.isClusterScopedDeployment()) {
            Arrays.stream(EntandoRbacRole.values())
                    .forEach(entandoRbacRole -> createRoleBindingForClusterRole(serviceAccountClient, deployable, entandoRbacRole));
        }
    }

    private void prepareServiceAccount(ServiceAccountClient serviceAccountClient, Deployable<?> deployable) {
        DoneableServiceAccount serviceAccount = serviceAccountClient
                .findOrCreateServiceAccount(entandoCustomResource, deployable.getServiceAccountToUse());
        List<LocalObjectReference> pullSecrets = serviceAccount.buildImagePullSecrets();
        serviceAccount.addAllToImagePullSecrets(EntandoOperatorConfig.getImagePullSecrets().stream()
                .filter(s -> pullSecrets.stream().noneMatch(pullSecret -> pullSecret.getName().equals(s))).map(LocalObjectReference::new)
                .collect(Collectors.toList())).done();
    }

    private Role newRole(Deployable<?> deployable) {
        return new RoleBuilder()
                .withNewMetadata()
                .withName(deployable.getServiceAccountToUse())
                .endMetadata()
                .withRules(forAllContainersIn(deployable))
                .build();
    }

    private List<PolicyRule> forAllContainersIn(Deployable<?> deployable) {
        return deployable.getContainers().stream()
                .map(DeployableContainer::getKubernetesPermissions)
                .flatMap(Collection::stream)
                .map(this::newPolicyRule).collect(Collectors.toList());
    }

    private RoleBinding newRoleBinding(Deployable<?> deployable) {
        return new RoleBindingBuilder()
                .withNewMetadata().withName(deployable.getServiceAccountToUse() + ROLEBINDING_SUFFIX)
                .endMetadata()
                .withNewRoleRef()
                //                .withApiGroup("rbac.authorization.k8s.io")
                .withName(role)
                .withKind("Role")
                .endRoleRef()
                .addNewSubject()
                //                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ServiceAccount")
                .withName(deployable.getServiceAccountToUse())
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .endSubject()
                .build();
    }

    private PolicyRule newPolicyRule(KubernetesPermission kubernetesPermission) {
        return new PolicyRuleBuilder()
                .withApiGroups(kubernetesPermission.getApiGroup())
                .withResources(kubernetesPermission.getResourceName())
                .withVerbs(kubernetesPermission.getVerbs())
                .build();
    }

    private void createRoleBindingForClusterRole(ServiceAccountClient serviceAccountClient, Deployable<?> deployable,
            EntandoRbacRole role) {
        serviceAccountClient.createRoleBindingIfAbsent(this.entandoCustomResource, new RoleBindingBuilder()
                .withNewMetadata().withName(deployable.getServiceAccountToUse() + "-" + role.getK8sName())
                .endMetadata()
                .withNewRoleRef()
                .withName(role.getK8sName())
                .withKind("ClusterRole")
                .endRoleRef()
                .addNewSubject()
                .withKind("ServiceAccount")
                .withName(deployable.getServiceAccountToUse())
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .endSubject()
                .build());
    }
}