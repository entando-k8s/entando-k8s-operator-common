package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.model.EntandoCustomResource;

public class TestControllerExecutor extends ControllerExecutor {

    public TestControllerExecutor(String controllerNamespace, KubernetesClient client) {
        super(controllerNamespace, client);
    }

    @Override
    protected String resolveImageVersion(EntandoCustomResource resource) {
        return EntandoOperatorE2ETestConfig.getVersionOfImageUnderTest().orElse("6.0.0-dev");
    }

}
