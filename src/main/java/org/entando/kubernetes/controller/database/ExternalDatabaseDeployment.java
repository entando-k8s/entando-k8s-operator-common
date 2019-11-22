package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;

public class ExternalDatabaseDeployment extends AbstractServiceResult {

    protected final Endpoints endpoints;
    protected final ExternalDatabase externalDatabase;

    public ExternalDatabaseDeployment(Service service, Endpoints endpoints, ExternalDatabase externalDatabase) {
        super(service);
        this.endpoints = endpoints;
        this.externalDatabase = externalDatabase;
    }

    public ExternalDatabase getExternalDatabase() {
        return externalDatabase;
    }

}
