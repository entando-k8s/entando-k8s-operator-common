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

package org.entando.kubernetes.test.sandbox.serialization;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.controller.spi.command.DefaultSerializableDeploymentResult;
import org.entando.kubernetes.controller.spi.command.SerializingDeployCommand;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.doubles.PodClientDouble;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.client.impl.PodWatcher;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.test.common.DatabaseDeployable;
import org.entando.kubernetes.test.common.DatabaseDeploymentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

//And experiment in JSON serialization
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
@EnableRuleMigrationSupport
class DeployableSerializationTest extends AbstractSupportK8SIntegrationTest {

    {
        System.setProperty(EntandoOperatorTestConfig.ENTANDO_TEST_EMULATE_KUBERNETES, "true");
    }

    @BeforeEach
    public void enableQueueing() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
        NonNamespaceOperation<CustomResourceDefinition, CustomResourceDefinitionList, Resource<CustomResourceDefinition>> crds = server
                .getClient().apiextensions().v1beta1().customResourceDefinitions();
        crds.createOrReplace(crds.load(EntandoApp.class.getResource("/crd/entandoapps.entando.org.crd.yaml")).get());
        prepareCrdNameMap();
    }

    @AfterEach
    void resetSystemProperty() {
        System.clearProperty(EntandoOperatorTestConfig.ENTANDO_TEST_EMULATE_KUBERNETES);
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        scheduler.shutdownNow();
        getSimpleK8SClient().pods().getPodWatcherQueue().clear();
    }

    protected void emulatePodWaitingBehaviour(EntandoApp app) {
        PodClientDouble.ENQUEUE_POD_WATCH_HOLDERS.set(true);
        scheduler.schedule(() -> {
            try {
                //The second watcher will trigger events
                PodWatcher controllerPodWatcher = getSimpleK8SClient().pods().getPodWatcherQueue().take();
                final Resource<Deployment> deploymentResource = getFabric8Client().apps().deployments()
                        .inNamespace(app.getMetadata().getNamespace())
                        .withName(app.getMetadata().getName() + "-db-deployment");
                await().atMost(60, TimeUnit.SECONDS).ignoreExceptions().until(
                        () -> deploymentResource.fromServer().get() != null);
                final Pod pod = getFabric8Client().pods().inNamespace(app.getMetadata().getNamespace())
                        .create(podFrom(deploymentResource.get()));
                controllerPodWatcher.eventReceived(Action.MODIFIED, podWithReadyStatus(pod));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }, 30, TimeUnit.MILLISECONDS);
    }

    @Test
    void testDatabaseDeployableSerialization() {
        final EntandoApp entandoApp = newTestEntandoApp();
        prepareCrdNameMap();
        getSimpleK8SClient().entandoResources().prepareConfig();
        getSimpleK8SClient().entandoResources().createOrPatchEntandoResource(entandoApp);
        final DatabaseDeployable originalDeployable = new DatabaseDeployable(DbmsDockerVendorStrategy.CENTOS_MYSQL, entandoApp, null);
        final SerializingDeployCommand serializingDeployCommand = new SerializingDeployCommand(getSimpleK8SClient().entandoResources(),
                new InProcessCommandStream(getSimpleK8SClient(), null));
        emulatePodWaitingBehaviour(entandoApp);
        final DatabaseDeploymentResult databaseDeploymentResult = serializingDeployCommand.processDeployable(originalDeployable);
        Deployable<DefaultSerializableDeploymentResult> serializedDeployable = serializingDeployCommand.getSerializedDeployable();
        verifyDeployable(serializedDeployable);
        verifyDeployableContainer(serializedDeployable);

        assertThat(databaseDeploymentResult.getDatabaseName(), is("my_app_db"));
        assertThat(databaseDeploymentResult.getAdminSecretName(), is("my-app-db-admin-secret"));
        assertThat(databaseDeploymentResult.getInternalServiceHostname(), is("my-app-db-service.my-app-namespace.svc.cluster.local"));
        assertThat(databaseDeploymentResult.getService().getMetadata().getName(), is("my-app-db-service"));
        final ServiceSpec spec = databaseDeploymentResult.getService().getSpec();
        assertThat(spec.getPorts().get(0).getPort(), is(3306));
        //        System.out.println(json);
    }

    private void verifyDeployableContainer(Deployable<DefaultSerializableDeploymentResult> serializedDeployable) {
        DeployableContainer o = serializedDeployable.getContainers().get(0);
        assertThat(o, is(instanceOf(ServiceBackingContainer.class)));
        assertThat(o, is(instanceOf(PersistentVolumeAware.class)));
        assertThat(((PersistentVolumeAware) o).getVolumeMountPath(), is("/var/lib/mysql/data"));
        assertThat(((PersistentVolumeAware) o).getStorageLimitMebibytes(), is(2048));
        assertThat(o, is(instanceOf(HasHealthCommand.class)));
        assertThat(((HasHealthCommand) o).getHealthCheckCommand(),
                is("MYSQL_PWD=${MYSQL_ROOT_PASSWORD} mysql -h 127.0.0.1 -u root -e 'SELECT 1'"));
        assertThat(o.getNameQualifier(), is("db"));
        assertThat(o.getPrimaryPort(), is(3306));
        assertThat(o.getDockerImageInfo().getRegistry().get(), is("docker.io"));
        assertThat(o.getDockerImageInfo().getOrganization().get(), is("centos"));
        assertThat(o.getDockerImageInfo().getRegistryHost().get(), is("docker.io"));
        assertThat(o.getDockerImageInfo().getRepository(), is("mysql-80-centos7"));
        assertThat(o.getDockerImageInfo().getVersion().get(), is("latest"));
    }

    private void verifyDeployable(Deployable<DefaultSerializableDeploymentResult> serializedDeployable) {
        assertThat(serializedDeployable.getFileSystemUserAndGroupId().get(), is(27L));
        assertThat(serializedDeployable.getQualifier().get(), is("db"));
        assertThat(serializedDeployable.getDefaultServiceAccountName(), is("default"));
        assertThat(serializedDeployable.getReplicas(), is(1));
        assertThat(serializedDeployable, is(instanceOf(Secretive.class)));
        final List<Secret> secrets = ((Secretive) serializedDeployable).getSecrets();
        assertThat(secrets.size(), is(1));
        final ObjectMeta metadata = secrets.get(0).getMetadata();
        assertThat(metadata.getName(), is("my-app-db-admin-secret"));
        assertThat(metadata.getLabels().get("EntandoApp"), is("my-app"));
        assertThat(metadata.getOwnerReferences().get(0).getKind(), is("EntandoApp"));
        assertThat(secrets.get(0).getStringData().get("username"), is("root"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{newTestEntandoApp().getMetadata().getNamespace()};
    }

    //
    //    @Test
    //    void testSamplePublicIngressingDbAwareDeployable() {
    //        new DeplDa
    //        DatabaseServiceResult databaseServiceResult = new DatabaseDeploymentResult();
    //        KeycloakConnectionConfig keycloakConnectionConfig = emulateKeycloakDeployment();
    //        final SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable =
    //                new SamplePublicIngressingDbAwareDeployable<>(
    //                newTestEntandoApp(), databaseServiceResult, keycloakConnectionConfig);
    //    }
}
