package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public class NamespaceDouble {

    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private final Map<String, Ingress> ingresses = new ConcurrentHashMap<>();
    private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
    private final Map<String, Pod> pods = new ConcurrentHashMap<>();
    private final Map<String, PersistentVolumeClaim> persistentVolumeClaims = new ConcurrentHashMap<>();
    private final Map<String, Endpoints> endpointsMap = new ConcurrentHashMap<>();
    private final Map<String, Secret> secrets = new ConcurrentHashMap<>();
    private final Map<String, ConfigMap> configMaps = new ConcurrentHashMap<>();
    private final Map<String, ServiceAccount> serviceAccounts = new ConcurrentHashMap<>();
    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final Map<String, RoleBinding> roleBindings = new ConcurrentHashMap<>();
    private final Map<Class<? extends EntandoCustomResource>, Map<String, EntandoCustomResource>> customResources = new
            ConcurrentHashMap<>();
    private final String name;

    public NamespaceDouble(String name) {
        this.name = name;
        customResources.put(EntandoApp.class, new ConcurrentHashMap<>());
        customResources.put(EntandoPlugin.class, new ConcurrentHashMap<>());
        customResources.put(KeycloakServer.class, new ConcurrentHashMap<>());
        customResources.put(ExternalDatabase.class, new ConcurrentHashMap<>());
        customResources.put(EntandoAppPluginLink.class, new ConcurrentHashMap<>());
        customResources.put(EntandoClusterInfrastructure.class, new ConcurrentHashMap<>());
    }

    public String getName() {
        return name;
    }

    public void putService(String name, Service service) {
        services.put(name, service);
    }

    public void putIngress(String name, Ingress ingress) {
        ingresses.put(name, ingress);
    }

    public Ingress getIngress(String name) {
        return ingresses.get(name);
    }

    public Service getService(String name) {
        return services.get(name);
    }

    public void putDeployment(String name, Deployment deployment) {
        this.deployments.put(name, deployment);
    }

    public Deployment getDeployment(String name) {
        return deployments.get(name);
    }

    public Pod getPod(String name) {
        return this.pods.get(name);
    }

    public void putPod(Pod pod) {
        this.pods.put(pod.getMetadata().getName(), pod);
    }

    public Map<String, Pod> getPods() {
        return pods;
    }

    public void putPersistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim) {
        this.persistentVolumeClaims.put(persistentVolumeClaim.getMetadata().getName(), persistentVolumeClaim);
    }

    public PersistentVolumeClaim getPersistentVolumeClaim(String name) {
        return persistentVolumeClaims.get(name);
    }

    public void putEndpoints(Endpoints endpoints) {
        this.endpointsMap.put(endpoints.getMetadata().getName(), endpoints);

    }

    public Endpoints getEndpoints(String name) {
        return endpointsMap.get(name);
    }

    public void putSecret(Secret secret) {
        this.secrets.put(secret.getMetadata().getName(), secret);
    }

    public Secret getSecret(String secretName) {
        return this.secrets.get(secretName);
    }

    public void putServiceAccount(ServiceAccount serviceAccount) {
        this.serviceAccounts.put(serviceAccount.getMetadata().getName(), serviceAccount);
    }

    public ServiceAccount getServiceAccount(String name) {
        return this.serviceAccounts.get(name);
    }

    public void putRole(Role role) {
        this.roles.put(role.getMetadata().getName(), role);
    }

    public Role getRole(String name) {
        return this.roles.get(name);
    }

    public void putRoleBinding(RoleBinding roleBinding) {
        this.roleBindings.put(roleBinding.getMetadata().getName(), roleBinding);
    }

    public RoleBinding getRoleBinding(String name) {
        return this.roleBindings.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> Map<String, T> getCustomResources(Class<T> customResource) {
        return (Map<String, T>) customResources.get(customResource);
    }

    public ConfigMap getConfigMap(String configMapName) {
        return configMaps.get(configMapName);
    }

    public void putConfigMap(ConfigMap configMap) {
        this.configMaps.put(configMap.getMetadata().getName(), configMap);
    }
}
