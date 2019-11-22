package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.Service;

public interface ServiceResult {

    String getInternalServiceHostname();

    String getPort();

    Service getService();
}
