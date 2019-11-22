package org.entando.kubernetes.controller.integrationtest.support;

import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;

public final class EntandoOperatorE2ETestConfig extends EntandoOperatorConfigBase {

    private EntandoOperatorE2ETestConfig() {
    }

    public static Optional<String> getKubernetesUsername() {
        return lookupProperty("entando.kubernetes.username");
    }

    public static Optional<String> getKubernetesPassword() {
        return lookupProperty("entando.kubernetes.password");
    }

    public static Optional<String> getKubernetesMasterUrl() {
        return lookupProperty("entando.kubernetes.master.url");
    }

    public static String getTestsCertRoot() {
        return lookupProperty(AbstractIntegrationTestHelper.TESTS_CERT_ROOT).orElse("src/test/resources/tls");
    }
}
