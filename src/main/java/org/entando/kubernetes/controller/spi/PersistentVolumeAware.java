package org.entando.kubernetes.controller.spi;

public interface PersistentVolumeAware {

    String getVolumeMountPath();

}
