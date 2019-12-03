package org.entando.kubernetes.client;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoControllerFailureBuilder;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseOperationFactory;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureOperationFactory;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerOperationFactory;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLinkOperationFactory;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginOperationFactory;

public class DefaultEntandoResourceClient implements EntandoResourceClient {

    private static final Map<Class<? extends EntandoCustomResource>, OperationsSupplier> operationSuppliers = new ConcurrentHashMap<>();

    static {
        operationSuppliers.put(KeycloakServer.class, KeycloakServerOperationFactory::produceAllKeycloakServers);
        operationSuppliers.put(EntandoClusterInfrastructure.class,
                EntandoClusterInfrastructureOperationFactory::produceAllEntandoClusterInfrastructures);
        operationSuppliers.put(EntandoApp.class, EntandoAppPluginLinkOperationFactory::produceAllEntandoAppPluginLinks);
        operationSuppliers.put(EntandoPlugin.class, EntandoPluginOperationFactory::produceAllEntandoPlugins);
        operationSuppliers.put(EntandoAppPluginLink.class, EntandoAppPluginLinkOperationFactory::produceAllEntandoAppPluginLinks);
        operationSuppliers.put(ExternalDatabase.class, ExternalDatabaseOperationFactory::produceAllExternalDatabases);
    }

    private final KubernetesClient client;
    private final Map<Class<? extends EntandoCustomResource>, CustomResourceOperationsImpl> customResourceOperations =
            new ConcurrentHashMap<>();

    public DefaultEntandoResourceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource) {
        String secretName = resource.getKeycloakSecretToUse().orElse(EntandoOperatorConfig.getDefaultKeycloakSecretName());
        return new KeycloakConnectionSecret(this.client.secrets().withName(secretName).fromServer().get());
    }

    @Override
    public InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource) {
        String secretName = EntandoOperatorConfig.getEntandoInfrastructureSecretName();
        return new InfrastructureConfig(this.client.secrets().withName(secretName).get());
    }

    @Override
    public ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource) {
        return new ServiceDeploymentResult(
                loadService(resource, standardServiceName(resource)),
                loadIngress(resource, standardIngressName(resource)));
    }

    public String standardServiceName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-server-service";
    }

    public String standardIngressName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + KubeUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public ExternalDatabaseDeployment findExternalDatabase(EntandoCustomResource resource) {
        List<ExternalDatabase> externalDatabaseList = getOperations(ExternalDatabase.class)
                .inNamespace(resource.getMetadata().getNamespace()).list().getItems();
        if (externalDatabaseList.isEmpty()) {
            return null;
        } else {
            ExternalDatabase externalDatabase = externalDatabaseList.get(0);
            return new ExternalDatabaseDeployment(
                    loadService(resource, externalDatabase.getMetadata().getName() + "-service"), null,
                    externalDatabase);
        }
    }

    @Override
    public void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status) {
        getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .edit()
                .withStatus(status)
                .done();

    }

    protected Supplier<IllegalStateException> notFound(String kind, String namespace, String name) {
        return () -> new IllegalStateException(format("Could not find the %s '%s' in the namespace %s", kind, name, namespace));
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return ofNullable(getOperations(clzz).inNamespace(resourceNamespace)
                .withName(resourceName).get()).orElseThrow(() -> notFound(clzz.getSimpleName(), resourceNamespace, resourceName).get());
    }

    @SuppressWarnings("unchecked")
    private <T extends EntandoCustomResource> CustomResourceOperationsImpl<T, KubernetesResourceList<T>,
            DoneableEntandoCustomResource<?, T>> getOperations(Class<T> c) {
        CustomResourceOperationsImpl result = this.customResourceOperations.get(c);
        if (result == null) {
            result = operationSuppliers.get(c).get(client);
            this.customResourceOperations.put(c, result);
        }
        return result;
    }

    @Override
    public void updatePhase(EntandoCustomResource customResource, EntandoDeploymentPhase phase) {
        getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .edit().withPhase(phase).done();

    }

    @Override
    public void deploymentFailed(EntandoCustomResource customResource, Exception reason) {
        Optional<AbstractServerStatus> currentServerStatus = getOperations(customResource.getClass())
                .inNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName()).get().getStatus().findCurrentServerStatus();
        if (currentServerStatus.isPresent()) {
            AbstractServerStatus newStatus = currentServerStatus.get();
            newStatus.finishWith(new EntandoControllerFailureBuilder().withException(reason).build());
            getOperations(customResource.getClass())
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName())
                    .edit().withStatus(newStatus).withPhase(EntandoDeploymentPhase.FAILED).done();
        } else {
            getOperations(customResource.getClass())
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName())
                    .edit().withPhase(EntandoDeploymentPhase.FAILED).done();

        }
    }

    private Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    private Ingress loadIngress(EntandoCustomResource peerInNamespace, String name) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    protected Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
