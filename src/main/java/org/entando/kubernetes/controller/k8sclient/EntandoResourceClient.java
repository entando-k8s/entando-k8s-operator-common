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

import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.RequiresKeycloak;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public interface EntandoResourceClient {

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    void updatePhase(EntandoCustomResource entandoCustomResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource resource, DbmsVendor vendor);

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
