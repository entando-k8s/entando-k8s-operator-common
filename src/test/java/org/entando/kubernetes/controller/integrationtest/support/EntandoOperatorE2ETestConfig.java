package org.entando.kubernetes.controller.integrationtest.support;

import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;

public final class EntandoOperatorE2ETestConfig extends EntandoOperatorConfigBase {

    private static final String ENTANDO_TEST_NAMESPACE = "entando.test.namespace";
    private static final String INTEGRATION_TARGET_ENVIRONMENT = "entando.k8s.operator.tests.run.target";
    private static final String TESTS_CERT_ROOT = "entando.k8s.operator.tests.cert.root";

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
        return lookupProperty(TESTS_CERT_ROOT).orElse("src/test/resources/tls");
    }

    public static TestTarget getTestTarget() {
        return lookupProperty(INTEGRATION_TARGET_ENVIRONMENT).map(String::toUpperCase).map(TestTarget::valueOf)
                .orElse(TestTarget.STANDALONE);
    }

    public static Optional<String> getTestNamespaceOverride() {
        return lookupProperty(ENTANDO_TEST_NAMESPACE);

    }

    public enum TestTarget {
        K8S, STANDALONE
    }
}
