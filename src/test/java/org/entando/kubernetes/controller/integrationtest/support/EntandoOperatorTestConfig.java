package org.entando.kubernetes.controller.integrationtest.support;

import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;

public final class EntandoOperatorTestConfig extends EntandoOperatorConfigBase {

    private static final String ENTANDO_TEST_NAMESPACE_OVERRIDE = "entando.test.namespace.override";
    private static final String ENTANDO_TEST_NAME_SUFFIX = "entando.test.name.suffix";
    private static final String ENTANDO_INTEGRATION_TARGET_ENVIRONMENT = "entando.k8s.operator.tests.run.target";
    private static final String ENTANDO_TESTS_CERT_ROOT = "entando.k8s.operator.tests.cert.root";
    private static final String ENTANDO_TEST_IMAGE_VERSION = "entando.test.image.version";
    private static final String ENTANDO_ORACLE_INTERNAL_HOST = "entando.oracle.internal.host";
    private static final String ENTANDO_ORACLE_EXTERNAL_HOST = "entando.oracle.external.host";
    private static final String ENTANDO_ORACLE_INTERNAL_PORT = "entando.oracle.internal.port";
    private static final String ENTANDO_ORACLE_EXTERNAL_PORT = "entando.oracle.external.port";
    private static final String ENTANDO_ORACLE_ADMIN_USER = "entando.oracle.admin.user";
    private static final String ENTANDO_ORACLE_ADMIN_PASSWORD = "entando.oracle.admin.password";
    private static final String ENTANDO_ORACLE_DATABASE_NAME = "entando.oracle.database.name";

    private EntandoOperatorTestConfig() {
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
        return lookupProperty(ENTANDO_TESTS_CERT_ROOT).orElse("src/test/resources/tls");
    }

    public static TestTarget getTestTarget() {
        return lookupProperty(ENTANDO_INTEGRATION_TARGET_ENVIRONMENT).map(String::toUpperCase).map(TestTarget::valueOf)
                .orElse(TestTarget.STANDALONE);
    }

    public static String calculateName(String baseName) {
        return baseName + getTestNameSuffix().map(s -> "-" + s).orElse("");
    }

    public static String calculateNameSpace(String baseName) {
        return calculateName(getTestNamespaceOverride().orElse(baseName));
    }

    public static Optional<String> getTestNamespaceOverride() {
        return lookupProperty(ENTANDO_TEST_NAMESPACE_OVERRIDE);
    }

    public static Optional<String> getTestNameSuffix() {
        return lookupProperty(ENTANDO_TEST_NAME_SUFFIX);
    }

    public static Optional<String> getVersionOfImageUnderTest() {
        return lookupProperty(ENTANDO_TEST_IMAGE_VERSION);
    }

    public static Optional<String> getOracleAdminPassword() {
        return lookupProperty(ENTANDO_ORACLE_ADMIN_PASSWORD);
    }

    public static Optional<String> getOracleAdminUser() {
        return lookupProperty(ENTANDO_ORACLE_ADMIN_USER);
    }

    public static Optional<String> getOracleInternalHost() {
        return lookupProperty(ENTANDO_ORACLE_INTERNAL_HOST);
    }

    public static Optional<String> getOracleExternalHost() {
        return lookupProperty(ENTANDO_ORACLE_EXTERNAL_HOST);
    }

    public static Optional<Integer> getOracleInternalPort() {
        return lookupProperty(ENTANDO_ORACLE_INTERNAL_PORT).map(Integer::parseInt);
    }

    public static Optional<Integer> getOracleExternalPort() {
        return lookupProperty(ENTANDO_ORACLE_EXTERNAL_PORT).map(Integer::parseInt);
    }

    public static Optional<String> getOracleDatabaseName() {
        return lookupProperty(ENTANDO_ORACLE_DATABASE_NAME);
    }

    public enum TestTarget {
        K8S, STANDALONE
    }
}
