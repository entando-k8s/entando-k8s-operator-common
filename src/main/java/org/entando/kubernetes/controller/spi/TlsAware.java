package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.creators.DeploymentCreator;

public interface TlsAware {

    @SuppressWarnings("squid:S2068")//Because it is not a hardcoded password
    default void addTlsVariables(List<EnvVar> vars) {
        vars.add(new EnvVar("JAVA_TOOL_OPTIONS",
                String.format("-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=%s", DeploymentCreator.TRUST_STORE_PATH,
                        TlsHelper.getInstance().getTrustStorePassword()), null));

    }
}
