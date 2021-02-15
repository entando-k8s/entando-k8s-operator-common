/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.test.e2etest.helpers;

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
import java.util.Collections;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.client.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.support.command.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.externaldatabase.DoneableEntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceList;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceOperationFactory;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceSpec;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceSpecBuilder;
import org.entando.kubernetes.test.e2etest.common.SampleWriter;
import org.entando.kubernetes.test.e2etest.podwaiters.ServicePodWaiter;

public class ExternalDatabaseE2ETestHelper extends
        E2ETestHelperBase<EntandoDatabaseService, EntandoDatabaseServiceList, DoneableEntandoDatabaseService> {

    public static final String MY_EXTERNAL_DB = EntandoOperatorTestConfig.calculateName("my-external-db");
    private static final String ORACLE_INTERNAL_HOST = EntandoOperatorTestConfig.getOracleInternalHost().orElse("localhost");
    private static final String ORACLE_EXTERNAL_HOST = EntandoOperatorTestConfig.getOracleExternalHost().orElse("localhost");
    private static final Integer ORACLE_INTERNAL_PORT = EntandoOperatorTestConfig.getOracleInternalPort().orElse(1521);
    private static final Integer ORACLE_EXTERNAL_PORT = EntandoOperatorTestConfig.getOracleExternalPort().orElse(1521);
    private static final String ORACLE_ADMIN_USER = EntandoOperatorTestConfig.getOracleAdminUser().orElse("admin");
    private static final String ORACLE_ADMIN_PASSWORD = EntandoOperatorTestConfig.getOracleAdminPassword().orElse("admin");
    private static final String ORACLE_DATABASE_NAME = EntandoOperatorTestConfig.getOracleDatabaseName().orElse("ORCLPDB1.localdomain");
    private static final String TEST_SECRET = "test-secret";

    public ExternalDatabaseE2ETestHelper(DefaultKubernetesClient client) {
        super(client, EntandoDatabaseServiceOperationFactory::produceAllEntandoDatabaseServices);
    }

    @SuppressWarnings("unchecked")
    public void prepareExternalPostgresqlDatabase(String namespace, String resourceKind) {
        deletePgTestPod(namespace);
        deleteCommonPreviousState(namespace);
        client.pods().inNamespace(namespace).createNew().withNewMetadata().withName("pg-test").addToLabels(resourceKind, null)
                .addToLabels(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resourceKind).endMetadata()
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
                .addToStringData(SecretUtils.USERNAME_KEY, "postgres").addToStringData(SecretUtils.PASSSWORD_KEY, "postgres")
                .build();
        SampleWriter.writeSample(secret, "postgresql-secret");
        client.secrets().inNamespace(namespace).create(secret);
        EntandoDatabaseService externalDatabase = new EntandoDatabaseServiceBuilder()
                .withNewMetadata()
                .withName(MY_EXTERNAL_DB)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.POSTGRESQL)
                .withHost(podIP)
                .withPort(5432)
                .withDatabaseName("testdb")
                .withSecretName(TEST_SECRET)
                .endSpec()
                .build();
        externalDatabase.getMetadata().setName(MY_EXTERNAL_DB);
        SampleWriter.writeSample(externalDatabase, "external-postgresql-db");
        createAndWaitForDbService(namespace, externalDatabase);
    }

    public void deletePgTestPod(String namespace) {
        delete(client.pods()).named("pg-test").fromNamespace(namespace).waitingAtMost(60, SECONDS);
    }

    private void deleteCommonPreviousState(String namespace) {
        delete(getOperations()).named(MY_EXTERNAL_DB).fromNamespace(namespace).waitingAtMost(15, SECONDS);
        delete(client.secrets()).named(TEST_SECRET).fromNamespace(namespace).waitingAtMost(15, SECONDS);
    }

    public void prepareExternalOracleDatabase(String namespace, String... usersToDrop) {
        deleteCommonPreviousState(namespace);
        Secret secret = new SecretBuilder().withNewMetadata().withName(TEST_SECRET).endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, ORACLE_ADMIN_USER)
                .addToStringData(SecretUtils.PASSSWORD_KEY, ORACLE_ADMIN_PASSWORD)
                .addToStringData("oracleTablespace", "USERS").build();
        SampleWriter.writeSample(secret, "oracle-secret");
        client.secrets().inNamespace(namespace).create(secret);
        EntandoDatabaseServiceSpec spec = new EntandoDatabaseServiceSpecBuilder().withDbms(DbmsVendor.ORACLE)
                .withHost(ORACLE_INTERNAL_HOST)
                .withPort(ORACLE_INTERNAL_PORT)
                .withDatabaseName(ORACLE_DATABASE_NAME)
                .withSecretName(TEST_SECRET)
                .withCreateDeployment(false)
                .withJdbcParameters(Collections.singletonMap("oracle.jdbc.timezoneAsRegion", "false"))
                .build();
        String externalJdbcUrl = DbmsDockerVendorStrategy.ORACLE.getVendorConfig().getConnectionStringBuilder().toHost(ORACLE_EXTERNAL_HOST)
                .onPort(ORACLE_EXTERNAL_PORT.toString()).usingDatabase(ORACLE_DATABASE_NAME).usingSchema(null)
                .buildConnectionString();
        dropUsers(externalJdbcUrl, usersToDrop);
        EntandoDatabaseService externalDatabase = new EntandoDatabaseService(spec);
        externalDatabase.getMetadata().setName(MY_EXTERNAL_DB);
        SampleWriter.writeSample(externalDatabase, "external-oracle-db");
        createAndWaitForDbService(namespace, externalDatabase);
    }

    protected void createAndWaitForDbService(String namespace, EntandoDatabaseService externalDatabase) {
        getOperations().inNamespace(namespace).create(externalDatabase);
        new CreateExternalServiceCommand(getOperations().inNamespace(namespace)
                .withName(externalDatabase.getMetadata().getName()).get())
                .execute(new DefaultSimpleK8SClient(client));
        await().atMost(60, SECONDS).until(
                () -> client.services().inNamespace(namespace).withName(MY_EXTERNAL_DB + "-db-service").fromServer().get()
                        != null);
    }

    protected void dropUsers(String jdbcUrl, String... users) {
        try {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, ORACLE_ADMIN_USER, ORACLE_ADMIN_PASSWORD)) {
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
