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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class ExternalDatabaseDeployment extends AbstractServiceResult {

    protected final Endpoints endpoints;
    protected final EntandoDatabaseService externalDatabase;

    public ExternalDatabaseDeployment(Service service, Endpoints endpoints, EntandoDatabaseService externalDatabase) {
        super(service);
        this.endpoints = endpoints;
        this.externalDatabase = externalDatabase;
    }

    public EntandoDatabaseService getEntandoDatabaseService() {
        return externalDatabase;
    }

}
