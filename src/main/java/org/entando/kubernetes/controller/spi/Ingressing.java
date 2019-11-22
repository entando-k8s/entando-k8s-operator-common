package org.entando.kubernetes.controller.spi;

import java.util.List;

public interface Ingressing<T extends IngressingPathOnPort> {

    List<T> getIngressingContainers();

    String getIngressName();

    String getIngressNamespace();

    String getNameQualifier();

}
