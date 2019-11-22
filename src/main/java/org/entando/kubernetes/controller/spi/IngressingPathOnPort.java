package org.entando.kubernetes.controller.spi;

public interface IngressingPathOnPort extends HasWebContext {

    int getPort();
}
