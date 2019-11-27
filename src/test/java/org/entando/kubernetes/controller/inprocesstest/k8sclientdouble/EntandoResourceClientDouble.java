package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public class EntandoResourceClientDouble extends AbstractK8SClientDouble implements EntandoResourceClient {

    public EntandoResourceClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    public void putEntandoApp(EntandoApp entandoApp) {
        putEntandoCustomResource(entandoApp);
    }

    public void putEntandoPlugin(EntandoPlugin entandoPlugin) {
        putEntandoCustomResource(entandoPlugin);
    }

    public <T extends EntandoCustomResource> void putEntandoCustomResource(T r) {
        this.getNamespace(r).getCustomResources((Class<T>) r.getClass()).put(r.getMetadata().getName(), r);
    }

    public void putExternalDatabase(ExternalDatabase externalDatabase) {
        putEntandoCustomResource(externalDatabase);
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status) {
        customResource.getStatus().putServerStatus(status);
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String namespace, String name) {
        Map<String, T> customResources = getNamespace(namespace).getCustomResources(clzz);
        return customResources.get(name);
    }

    @Override
    public void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase) {
        entandoCustomResource.getStatus().setEntandoDeploymentPhase(phase);
    }

    @Override
    public void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason) {
        entandoCustomResource.getStatus().findCurrentServerStatus().get()
                .finishWith(new EntandoControllerFailureBuilder().withException(reason).build());
    }

    @Override
    public ExternalDatabaseDeployment findExternalDatabase(EntandoCustomResource resource) {
        NamespaceDouble namespace = getNamespace(resource);
        Optional<ExternalDatabase> first = namespace.getCustomResources(ExternalDatabase.class).values().stream().findFirst();
        return first.map(edb -> new ExternalDatabaseDeployment(
                namespace.getService(edb.getMetadata().getName() + "-" + KubeUtils.DEFAULT_SERVICE_SUFFIX),
                namespace.getEndpoints(edb.getMetadata().getName() + "-endpoints"), edb)).orElse(null);
    }

    @Override
    public KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource) {
        return new KeycloakConnectionSecret(getNamespaces().get(CONTROLLER_NAMESPACE)
                .getSecret(resource.getKeycloakSecretToUse().orElse(EntandoOperatorConfig.getDefaultKeycloakSecretName())));
    }

    @Override
    public InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource) {
        return new InfrastructureConfig(getNamespaces().get(CONTROLLER_NAMESPACE)
                .getSecret(EntandoOperatorConfig.getEntandoInfrastructureSecretName()));
    }

    @Override
    public ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource) {
        NamespaceDouble namespace = getNamespace(resource);
        Service service = namespace.getService(
                resource.getMetadata().getName() + "-" + KubeUtils.DEFAULT_SERVER_QUALIFIER + "-" + KubeUtils.DEFAULT_SERVICE_SUFFIX);
        Ingress ingress = namespace.getIngress(KubeUtils.standardIngressName(resource));
        return new ServiceDeploymentResult(service, ingress);
    }

}
