package org.entando.kubernetes.controller.k8sclient;

import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public interface EntandoResourceClient {

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T putEntandoCustomResource(T r);

    void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    ExternalDatabaseDeployment findExternalDatabase(EntandoCustomResource resource);

    KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource);

    InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource);

    ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource);

    default EntandoApp loadEntandoApp(String namespace, String name) {
        return load(EntandoApp.class, namespace, name);
    }

    default EntandoPlugin loadEntandoPlugin(String namespace, String name) {
        return load(EntandoPlugin.class, namespace, name);
    }

}
