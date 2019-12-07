package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;
import static org.entando.kubernetes.controller.Wait.waitFor;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.externaldatabase.DoneableExternalDatabase;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseList;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseOperationFactory;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseSpec;

public class ExternalDatabaseIntegrationTestHelper extends
        IntegrationTestHelperBase<ExternalDatabase, ExternalDatabaseList, DoneableExternalDatabase> {

    public static final String MY_EXTERNAL_DB = EntandoOperatorE2ETestConfig.calculateName("my-external-db");
    private static final String ADMIN = "admin";
    private static final String TEST_SECRET = "test-secret";

    public ExternalDatabaseIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, ExternalDatabaseOperationFactory::produceAllExternalDatabases);
    }

    @SuppressWarnings("unchecked")
    public void prepareExternalPostgresqlDatabase(String namespace) {
        if (client.pods().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName("pg-test").fromServer()
                .get() != null) {
            deletePreviousState();

        }
        client.pods().inNamespace(namespace).createNew().withNewMetadata().withName("pg-test").endMetadata()
                .withNewSpec().addNewContainer()
                .withName("pg-container")
                .withImage("centos/postgresql-96-centos7:latest")
                .withNewReadinessProbe()
                .withNewExec()
                .addToCommand("/bin/sh", "-i", "-c",
                        "psql -h 127.0.0.1 -U ${POSTGRESQL_USER} -q -d postgres -c '\\l'|grep ${POSTGRESQL_DATABASE}")
                .endExec()
                .endReadinessProbe()
                .withEnv(new EnvVar("POSTGRESQL_USER", "testUser", null),
                        new EnvVar("POSTGRESQL_PASSWORD", "test123", null),
                        new EnvVar("POSTGRESQL_DATABASE", "testdb", null),
                        new EnvVar("POSTGRESQL_ADMIN_PASSWORD", "postgres", null))
                .endContainer().endSpec().done();
        PodResource<Pod, DoneablePod> podResource = client.pods().inNamespace(namespace).withName("pg-test");
        new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(60)).throwException(RuntimeException.class)
                .waitOn(podResource);
        String podIP = podResource.fromServer().get().getStatus().getPodIP();
        Secret secret = new SecretBuilder().withNewMetadata().withName(TEST_SECRET).endMetadata()
                .addToStringData(KubeUtils.USERNAME_KEY, "postgres").addToStringData(KubeUtils.PASSSWORD_KEY, "postgres")
                .build();
        SampleWriter.writeSample(secret, "postgresql-secret");
        client.secrets().inNamespace(namespace).create(secret);
        ExternalDatabase externalDatabase = new ExternalDatabase(
                new ExternalDatabaseSpec(DbmsImageVendor.POSTGRESQL, podIP, 5432, "testdb", TEST_SECRET));
        externalDatabase.getMetadata().setName(MY_EXTERNAL_DB);
        SampleWriter.writeSample(externalDatabase, "external-postgresql-db");
        createAndWaitForDbService(namespace, externalDatabase);
    }

    protected void deletePreviousState() {
        client.pods().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName("pg-test").delete();
        deleteCommonPreviousState();
        await().atMost(60, TimeUnit.SECONDS).ignoreExceptions().until(() ->
                client.pods().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName("pg-test")
                        .fromServer()
                        .get() == null);
    }

    private void deleteCommonPreviousState() {
        try {
            getOperations().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                    .withName(MY_EXTERNAL_DB).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            getOperations().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE)
                    .withName(MY_EXTERNAL_DB).delete();
            client.secrets().inNamespace(EntandoPluginIntegrationTestHelper.TEST_PLUGIN_NAMESPACE).withName("test-secret")
                    .delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void prepareExternalOracleDatabase(String namespace, String... usersToDrop) {
        deleteCommonPreviousState();
        Secret secret = new SecretBuilder().withNewMetadata().withName(TEST_SECRET).endMetadata()
                .addToStringData(KubeUtils.USERNAME_KEY, ADMIN)
                .addToStringData(KubeUtils.PASSSWORD_KEY, ADMIN)
                .addToStringData("oracleMavenRepo", "http://10.0.0.100:8081/repository/maven-releases/")
                .addToStringData("oracleRepoUser", ADMIN)
                .addToStringData("oracleRepoPassword", "admin123")
                .addToStringData("oracleTablespace", "USERS").build();
        SampleWriter.writeSample(secret, "oracle-secret");
        client.secrets().inNamespace(namespace).create(secret);
        ExternalDatabaseSpec spec = new ExternalDatabaseSpec(DbmsImageVendor.ORACLE, K8SIntegrationTestHelper.ORACLE_HOST, 1521,
                "XEPDB1",
                TEST_SECRET);
        String jdbcUrl = DbmsImageVendor.ORACLE.getConnectionStringBuilder().toHost(spec.getHost())
                .onPort(spec.getPort().get().toString()).usingDatabase(spec.getDatabaseName()).usingSchema(null)
                .buildConnectionString();
        dropUsers(jdbcUrl, usersToDrop);
        ExternalDatabase externalDatabase = new ExternalDatabase(spec);
        externalDatabase.getMetadata().setName(MY_EXTERNAL_DB);
        SampleWriter.writeSample(externalDatabase, "external-oracle-db");
        createAndWaitForDbService(namespace, externalDatabase);
    }

    protected void createAndWaitForDbService(String namespace, ExternalDatabase externalDatabase) {
        getOperations().inNamespace(namespace)
                .create(externalDatabase);
        new CreateExternalServiceCommand(getOperations().inNamespace(externalDatabase.getMetadata().getNamespace())
                .withName(externalDatabase.getMetadata().getName()).get())
                .execute(new DefaultSimpleK8SClient(client));
        waitFor(60).seconds().until(
                () -> client.services().inNamespace(namespace).withName(MY_EXTERNAL_DB + "-service").fromServer().get()
                        != null);
    }

    protected void dropUsers(String jdbcUrl, String... users) {
        try {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, ADMIN, ADMIN)) {
                for (String user : users) {
                    try {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("DROP USER " + user + " CASCADE");
                        }
                    } catch (SQLException e) {
                        logWarning(e.toString());
                    }

                }
            }
        } catch (SQLException e) {
            logWarning(e.toString());
        }
    }

}
