package org.entando.kubernetes.controller.integrationtest.support;

import org.entando.kubernetes.controller.inprocesstest.FluentTraversals;
import org.entando.kubernetes.controller.integrationtest.support.TestFixtureRequest.DeletionRequestBuilder;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public interface FluentIntegrationTesting extends FluentTraversals {

    default DeletionRequestBuilder deleteAll(Class<? extends EntandoBaseCustomResource>... types) {
        return new TestFixtureRequest().deleteAll(types);
    }
}
