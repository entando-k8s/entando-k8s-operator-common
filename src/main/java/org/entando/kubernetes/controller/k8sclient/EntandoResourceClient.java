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

    EntandoApp loadEntandoApp(String namespace, String name);

    EntandoPlugin loadEntandoPlugin(String namespace, String name);

    void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    ExternalDatabaseDeployment findExternalDatabase(EntandoCustomResource resource);

    KeycloakConnectionConfig findKeycloak(RequiresKeycloak resource);

    InfrastructureConfig findInfrastructureConfig(EntandoCustomResource resource);

    ServiceDeploymentResult loadServiceResult(EntandoCustomResource resource);
}
