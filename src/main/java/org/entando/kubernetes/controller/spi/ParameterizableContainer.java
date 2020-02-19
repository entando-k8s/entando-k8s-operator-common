package org.entando.kubernetes.controller.spi;

import org.entando.kubernetes.model.EntandoDeploymentSpec;

public interface ParameterizableContainer extends DeployableContainer {
    EntandoDeploymentSpec getCustomResourceSpec();

}
