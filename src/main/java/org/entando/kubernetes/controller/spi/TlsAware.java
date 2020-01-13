package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.creators.SecretCreator;

public interface TlsAware {

    default void addTlsVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("JAVA_TOOL_OPTIONS",
                null,
                KubeUtils.secretKeyRef(SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, SecretCreator.TRUSTSTORE_SETTINGS_KEY)));

    }
}
