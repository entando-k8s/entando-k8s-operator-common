package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;

public interface DatabasePopulator {

    String determineImageToUse();

    String[] getCommand();

    void addEnvironmentVariables(List<EnvVar> vars);

}
