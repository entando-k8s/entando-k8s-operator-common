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

package org.entando.kubernetes.controller;

import static java.lang.String.format;
import static java.util.Optional.empty;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.database.DbmsVendorStrategy;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;

public abstract class AbstractDbAwareController<T extends EntandoBaseCustomResource> {

    protected final SimpleK8SClient<?> k8sClient;
    protected final SimpleKeycloakClient keycloakClient;
    protected final AutoExit autoExit;
    protected final EntandoImageResolver entandoImageResolver;
    protected Class<T> resourceType;
    protected Logger logger;

    /**
     * Constructor for runtime environments where the KubernetesClient is injected, and the controller is assumed to exit automatically to
     * emulate the behavior of a normal CLI.
     */

    protected AbstractDbAwareController(KubernetesClient kubernetesClient) {
        this(new DefaultSimpleK8SClient(kubernetesClient), new DefaultKeycloakClient(), new AutoExit(true));
    }

    /**
     * Constructor for in process tests where we may want to mock the clients out and would not want to exit.
     */

    protected AbstractDbAwareController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        this(k8sClient, keycloakClient, new AutoExit(false));
    }

    /**
     * Constructor for integration tests where we would need to override the auto exit behaviour.
     */
    public AbstractDbAwareController(KubernetesClient kubernetesClient, boolean exitAutomatically) {
        this(new DefaultSimpleK8SClient(kubernetesClient), new DefaultKeycloakClient(), new AutoExit(exitAutomatically));

    }

    private AbstractDbAwareController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient, AutoExit autoExit) {
        this.k8sClient = k8sClient;
        this.keycloakClient = keycloakClient;
        this.autoExit = autoExit;
        this.entandoImageResolver = new EntandoImageResolver(
                k8sClient.secrets().loadControllerConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap()));
        Class<?> cls = getClass();
        List<Class<T>> types = new ArrayList<>();
        while (cls != AbstractDbAwareController.class) {
            if (isImplementedCorrectly(cls)) {
                types.add(getFirstTypeArgument(cls));
            }
            cls = cls.getSuperclass();
        }
        if (types.isEmpty()) {
            throw new IllegalStateException(
                    "Please implement " + AbstractDbAwareController.class.getSimpleName() + " directly with the required type argument.");
        }
        this.resourceType = types.get(types.size() - 1);
        this.logger = Logger.getLogger(resourceType.getName() + "Controller");
    }

    @SuppressWarnings("unchecked")
    private Class<T> getFirstTypeArgument(Class<?> cls) {
        ParameterizedType genericSuperclass = (ParameterizedType) cls.getGenericSuperclass();
        return (Class<T>) genericSuperclass.getActualTypeArguments()[0];
    }

    private boolean isImplementedCorrectly(Class<?> cls) {
        if (cls.getGenericSuperclass() instanceof ParameterizedType) {
            ParameterizedType genericSuperclass = (ParameterizedType) cls.getGenericSuperclass();
            if (genericSuperclass.getActualTypeArguments().length == 1 && genericSuperclass.getActualTypeArguments()[0] instanceof Class) {
                Class<?> argument = (Class<?>) genericSuperclass.getActualTypeArguments()[0];
                return EntandoBaseCustomResource.class.isAssignableFrom(argument);
            }
        }
        return false;
    }

    protected abstract void synchronizeDeploymentState(T newResource);

    protected void processCommand() {
        try {
            Action action = Action.valueOf(
                    EntandoOperatorConfigBase.lookupProperty(KubeUtils.ENTANDO_RESOURCE_ACTION).orElseThrow(IllegalArgumentException::new));
            TlsHelper.getInstance().init();
            if (actionToProcess(action)) {
                String resourceName = EntandoOperatorConfigBase.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAME)
                        .orElseThrow(IllegalArgumentException::new);
                String resourceNamespace = EntandoOperatorConfigBase.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE)
                        .orElseThrow(IllegalArgumentException::new);
                T newResource = k8sClient.entandoResources().load(resourceType, resourceNamespace, resourceName);
                processAction(action, newResource);
            }
        } finally {
            new Thread(autoExit).start();
        }

    }

    private boolean actionToProcess(Action action) {
        return action == Action.ADDED || action == Action.MODIFIED;
    }

    protected void processAction(Action action, T resource) {
        try {
            if (action == Action.ADDED || action == Action.MODIFIED) {
                EntandoDeploymentPhase initialPhase = resource.getStatus().getEntandoDeploymentPhase();
                k8sClient.entandoResources().updatePhase(resource, EntandoDeploymentPhase.STARTED);
                if (action == Action.ADDED || initialPhase == EntandoDeploymentPhase.REQUESTED) {
                    synchronizeDeploymentState(resource);
                }
                k8sClient.entandoResources().updatePhase(resource, EntandoDeploymentPhase.SUCCESSFUL);
            }
        } catch (Exception e) {
            autoExit.withCode(-1);
            logger.log(Level.SEVERE, e, () -> format("Unexpected exception occurred while adding %s %s/%s",
                    resource.getKind(),
                    resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName()));
            k8sClient.entandoResources().deploymentFailed(resource, e);
        }
    }

    protected DatabaseServiceResult prepareDatabaseService(EntandoCustomResource entandoCustomResource, DbmsVendor dbmsVendor,
            String nameQualifier) {
        Optional<ExternalDatabaseDeployment> externalDatabase = k8sClient.entandoResources()
                .findExternalDatabase(entandoCustomResource, dbmsVendor);
        DatabaseServiceResult result = null;
        if (externalDatabase.isPresent()) {
            result = new DatabaseServiceResult(externalDatabase.get().getService(), externalDatabase.get().getEntandoDatabaseService());
        } else if (!(dbmsVendor == DbmsVendor.NONE || dbmsVendor == DbmsVendor.EMBEDDED)) {
            final DatabaseDeployable databaseDeployable = new DatabaseDeployable(DbmsVendorStrategy.forVendor(dbmsVendor),
                    entandoCustomResource, nameQualifier);
            final DeployCommand<DatabaseServiceResult> dbCommand = new DeployCommand<>(databaseDeployable);
            result = dbCommand.execute(k8sClient, empty());
            if (result.hasFailed()) {
                throw new EntandoControllerException("Database deployment failed");
            }
        }
        return result;
    }
}
