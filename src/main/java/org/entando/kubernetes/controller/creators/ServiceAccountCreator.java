package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.k8sclient.ServiceAccountClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.KubernetesPermission;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceAccountCreator extends AbstractK8SResourceCreator {

    public static final String ROLEBINDING_SUFFIX = "-rolebinding";
    private String serviceAccount;
    private String role;

    public ServiceAccountCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public String prepareServiceAccount(ServiceAccountClient serviceAccountClient, Deployable<?> deployable) {
        this.serviceAccount = serviceAccountClient.createServiceAccountIfAbsent(entandoCustomResource, newServiceAccount(deployable));
        this.role = serviceAccountClient.createRoleIfAbsent(entandoCustomResource, newRole(deployable));
        serviceAccountClient.createRoleBindingIfAbsent(entandoCustomResource, newRoleBinding());
        return serviceAccount;
    }

    private Role newRole(Deployable<?> deployable) {
        return new RoleBuilder()
                .withNewMetadata()
                .withName(deployable.getServiceAccountName())
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

    private RoleBinding newRoleBinding() {
        return new RoleBindingBuilder()
                .withNewMetadata().withName(serviceAccount + ROLEBINDING_SUFFIX)
                .endMetadata()
                .withNewRoleRef()
                //                .withApiGroup("rbac.authorization.k8s.io")
                .withName(role)
                .withKind("Role")
                .endRoleRef()
                .addNewSubject()
                //                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ServiceAccount")
                .withName(serviceAccount)
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

    private ServiceAccount newServiceAccount(Deployable deployable) {
        return new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(deployable.getServiceAccountName())
                .endMetadata()
                .build();
    }

}
