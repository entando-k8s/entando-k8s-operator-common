package org.entando.kubernetes.controller.spi;

import java.util.Optional;

public interface HasWebContext {

    String getWebContextPath();

    Optional<String> getHealthCheckPath();

}
