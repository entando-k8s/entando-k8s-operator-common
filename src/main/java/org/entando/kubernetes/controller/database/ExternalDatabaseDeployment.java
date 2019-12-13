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
