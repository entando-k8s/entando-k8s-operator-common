package org.entando.kubernetes.controller.test


import io.fabric8.kubernetes.client.Watcher
import org.entando.kubernetes.client.PodWatcher
import org.entando.kubernetes.controller.DeployCommand
import org.entando.kubernetes.controller.common.examples.BareBonesContainer
import org.entando.kubernetes.controller.common.examples.BareBonesDeployable
import org.entando.kubernetes.controller.creators.DeploymentCreator
import org.entando.kubernetes.controller.creators.ServiceAccountCreator
import org.entando.kubernetes.controller.test.support.DefaultSecretBasedCredentials
import org.entando.kubernetes.controller.test.support.PodBehavior
import org.entando.kubernetes.controller.test.support.TestFixtureFactory
import org.entando.kubernetes.model.DbmsImageVendor
import org.entando.kubernetes.model.EntandoCustomResource
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static org.awaitility.Awaitility.await

abstract class BareBonesDeployableSpecBase extends Specification implements TestFixtureFactory, PodBehavior {

    def "the secret is built and is mounted in the standard secret mount root"() {

        given: "I have created an Entando Custom resource"
        def k8sClient = getClient()
        def customResource = newEntandoApp()
        k8sClient.entandoResources().putEntandoCustomResource(customResource)
        and: "I have a basic deployable implementations"
        def deployable = new BareBonesDeployable(customResource)
        emulatePodWaitBehavior(customResource, TEST_APP_NAME + "-" + BareBonesDeployable.NAME_QUALIFIER + DeploymentCreator.DEPLOYMENT_SUFFIX)

        when: "process the deployable"
        new DeployCommand<>(deployable).execute(k8sClient, Optional.empty())

        then: "I expect to see a secret created as specified by the BareBonesDeployable"
        def credentials = new DefaultSecretBasedCredentials(k8sClient.secrets().loadSecret(customResource, BareBonesContainer.DATABASE_SECRET_NAME))
        credentials.username == DbmsImageVendor.POSTGRESQL.defaultAdminUsername
        credentials.password
        and: "the secret is mounted in the standard location"
        def deployment = k8sClient.deployments().loadDeployment(customResource, TEST_APP_NAME + "-" + BareBonesDeployable.NAME_QUALIFIER + DeploymentCreator.DEPLOYMENT_SUFFIX)
        deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath == DeploymentCreator.ENTANDO_SECRET_MOUNTS_ROOT + "/" + BareBonesContainer.DATABASE_SECRET_NAME
        deployment.spec.template.spec.containers[0].volumeMounts[0].name == BareBonesContainer.DATABASE_SECRET_NAME + DeploymentCreator.VOLUME_SUFFIX
        and: "the volume is mapped to the previously created secret"
        deployment.spec.template.spec.volumes[0].name == BareBonesContainer.DATABASE_SECRET_NAME + DeploymentCreator.VOLUME_SUFFIX
        deployment.spec.template.spec.volumes[0].secret.secretName == BareBonesContainer.DATABASE_SECRET_NAME
    }

    def "the serviceaccount is created with the necessary role based access to the specified resources in the namespace"() {

        given: "I have created an Entando Custom resource"
        def k8sClient = getClient()
        def customResource = newEntandoApp()
        k8sClient.entandoResources().putEntandoCustomResource(customResource)
        and: "I have a basic deployable implementations"
        def deployable = new BareBonesDeployable(customResource)
        emulatePodWaitBehavior(customResource, TEST_APP_NAME + "-" + BareBonesDeployable.NAME_QUALIFIER + DeploymentCreator.DEPLOYMENT_SUFFIX)

        when: "process the deployable"
        new DeployCommand<>(deployable).execute(k8sClient, Optional.empty())

        then: "I expect to see a service account created as specified by the BareBonesDeployable"
        def account = k8sClient.serviceAccounts().loadServiceAccount(customResource, BareBonesDeployable.MY_SERVICE_ACCOUNT)
        account
        def role = k8sClient.serviceAccounts().loadRole(customResource, BareBonesDeployable.MY_SERVICE_ACCOUNT)
        role.rules[0].apiGroups[0] == "entando.org"
        role.rules[0].resources[0] == "EntandoApp"
        role.rules[0].verbs.containsAll("get", "create")
        def roleBinding = k8sClient.serviceAccounts().loadRoleBinding(customResource, BareBonesDeployable.MY_SERVICE_ACCOUNT + ServiceAccountCreator.ROLEBINDING_SUFFIX)
        roleBinding.roleRef.name == BareBonesDeployable.MY_SERVICE_ACCOUNT
        roleBinding.roleRef.kind == "Role"
        roleBinding.subjects[0].kind == "ServiceAccount"
        roleBinding.subjects[0].name == BareBonesDeployable.MY_SERVICE_ACCOUNT
    }

    def "the deployment is created reflecting the specs of the BarBoneDeployable"() {

        given: "I have created an Entando Custom resource"
        def k8sClient = getClient()
        def customResource = newEntandoApp()
        k8sClient.entandoResources().putEntandoCustomResource(customResource)
        and: "I have a basic deployable implementations"
        def deployable = new BareBonesDeployable(customResource)
        emulatePodWaitBehavior(customResource, TEST_APP_NAME + "-" + BareBonesDeployable.NAME_QUALIFIER + DeploymentCreator.DEPLOYMENT_SUFFIX)

        when: "process the deployable"
        new DeployCommand<>(deployable).execute(k8sClient, Optional.empty())

        then: "I expect to see a deployment created with a name reflecting the custom resource name and the BareBonesDeployable's name qualifier"
        def deployment = k8sClient.deployments().loadDeployment(customResource, customResource.metadata.name + "-" + BareBonesDeployable.NAME_QUALIFIER + DeploymentCreator.DEPLOYMENT_SUFFIX)
        deployment.spec.template.spec.serviceAccountName == BareBonesDeployable.MY_SERVICE_ACCOUNT

        and: "and a container and port reflecting the name qualifier of the BarBonesContainer"
        deployment.spec.template.spec.containers[0].name == BareBonesContainer.NAME_QUALIFIER + DeploymentCreator.CONTAINER_SUFFIX
        deployment.spec.template.spec.containers[0].ports[0].name == BareBonesContainer.NAME_QUALIFIER + DeploymentCreator.PORT_SUFFIX
        and: "and the resource limits specified by the BareBonesDeployableContainer "
        deployment.spec.template.spec.containers[0].resources.limits["memory"].amount == BareBonesContainer.MEMORY_LIMIT + "Mi"
        deployment.spec.template.spec.containers[0].resources.limits["cpu"].amount == BareBonesContainer.CPU_LIMIT + "m"

        and: "and the resource request that are a quarter of the resource lmits "
        deployment.spec.template.spec.containers[0].resources.requests["memory"].amount == (BareBonesContainer.MEMORY_LIMIT / 4) + "Mi"
        deployment.spec.template.spec.containers[0].resources.requests["cpu"].amount == (BareBonesContainer.CPU_LIMIT / 4) + "m"

    }

    def emulatePodWaitBehavior(EntandoCustomResource resource, String deploymentName) {
        new Thread({
            AtomicReference<PodWatcher> podWatcherHolder = getClient().pods().getPodWatcherHolder()
            await().atMost(30, TimeUnit.SECONDS).until({ podWatcherHolder.get() != null })
            def deployment = this.client.deployments().loadDeployment(resource, deploymentName)
            podWatcherHolder.getAndSet(null).eventReceived(Watcher.Action.MODIFIED, podWithSucceededStatus(deployment))
        }).start()

    }
}
