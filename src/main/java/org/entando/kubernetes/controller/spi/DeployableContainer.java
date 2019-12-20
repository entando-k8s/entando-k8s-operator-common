package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Collections;
import java.util.List;

public interface DeployableContainer {

    String determineImageToUse();

    String getNameQualifier();

    int getPort();

    default void addEnvironmentVariables(List<EnvVar> vars) {
        //to avoid the need for repeated empty implementations
    }
    default int getMemoryLimitMebibytes(){
        return 256;
    }
    default int getCpuLimitMillicores(){
        return 500;
    }
    default List<String> getNamesOfSecretsToMount() {
        return Collections.emptyList();
    }

    default List<KubernetesPermission> getKubernetesPermissions() {
        return Collections.emptyList();
    }

}
