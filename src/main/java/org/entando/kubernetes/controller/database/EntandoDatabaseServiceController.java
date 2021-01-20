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

package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.common.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceSpec;

public class EntandoDatabaseServiceController extends AbstractDbAwareController<EntandoDatabaseServiceSpec, EntandoDatabaseService> {

    /**
     * Constructor for integration tests where we would need to override the auto exit behaviour.
     */
    public EntandoDatabaseServiceController(KubernetesClient client) {
        super(client, false);
    }

    /**
     * Constructor for in process tests where we may want to mock the clients out and would not want to exit.
     */
    public EntandoDatabaseServiceController(SimpleK8SClient<?> k8sClient) {
        super(k8sClient, null);
    }

    public void processEvent(Action action, EntandoDatabaseService db) {
        if (actionRequiresSync(action)) {
            super.performSync(db);
        }
    }

    @Override
    protected void synchronizeDeploymentState(EntandoDatabaseService entandoDatabaseService) {
        new CreateExternalServiceCommand(entandoDatabaseService).execute(super.k8sClient);
    }
}
