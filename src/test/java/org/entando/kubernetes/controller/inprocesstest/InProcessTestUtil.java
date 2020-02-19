package org.entando.kubernetes.controller.inprocesstest;

import static org.entando.kubernetes.controller.PodResult.RUNNING_PHASE;
import static org.entando.kubernetes.controller.PodResult.SUCCEEDED_PHASE;

import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.test.support.VolumeMatchAssertions;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.JeeServer;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.PluginSecurityLevel;

/**
 * Mostly a source of test fixture factories. TODO: These need to refactored to be inter-process friendly
 */
public interface InProcessTestUtil extends VolumeMatchAssertions, K8SStatusBasedAnswers, K8SResourceArgumentMatchers,
        StandardArgumentCaptors {

    String DEPLOYMENT_LABEL_NAME = DeployCommand.DEPLOYMENT_LABEL_NAME;
    String ENTANDO_PLUGIN_LABEL_NAME = "EntandoPlugin";
    String ENTANDO_APP_LABEL_NAME = "EntandoApp";
    String ENTANDO_CLUSTER_INFRASTRUCTURE_LABEL_NAME = "EntandoClusterInfrastructure";
    String KEYCLOAK_SERVER_LABEL_NAME = "EntandoKeycloakServer";
    String ENTANDO_APP_PLUGIN_LINK_LABEL_NAME = "EntandoAppPluginLink";
    String ENTANDO_KEYCLOAK_REALM = KubeUtils.ENTANDO_KEYCLOAK_REALM;
    String KEYCLOAK_SECRET = "ASDFASDFAS";
    String TCP = "TCP";
    String MY_KEYCLOAK = "my-keycloak";
    String MY_KEYCLOAK_ADMIN_USERNAME = "entando_keycloak_admin";
    String MY_KEYCLOAK_ADMIN_PASSWORD = MY_KEYCLOAK_ADMIN_USERNAME + "123";
    String TLS_SECRET = "tls-secret";
    String MY_KEYCLOAK_TLS_SECRET = MY_KEYCLOAK + "-" + TLS_SECRET;
    String NAMESPACE = "namespace";
    String MY_KEYCLOAK_NAMESPACE = MY_KEYCLOAK + "-" + NAMESPACE;
    String DEFAULT_KEYCLOAK_ADMIN_SECRET = EntandoOperatorConfig.getDefaultKeycloakSecretName();//Stick to the default
    String MY_CLUSTER_INFRASTRUCTURE = "my-eci";
    String MY_CLUSTER_INFRASTRUCTURE_TLS_SECRET = MY_CLUSTER_INFRASTRUCTURE + "-" + TLS_SECRET;
    String MY_CLUSTER_INFRASTRUCTURE_NAMESPACE = MY_CLUSTER_INFRASTRUCTURE + "-" + NAMESPACE;
    String MY_APP = "my-app";
    String MY_APP_TLS_SECRET = MY_APP + "-" + TLS_SECRET;
    String MY_APP_NAMESPACE = MY_APP + "-" + NAMESPACE;
    String MY_PLUGIN = "my-plugin";
    String MY_PLUGIN_NAMESPACE = MY_PLUGIN + "-" + NAMESPACE;
    String MY_KEYCLOAK_HOSTNAME = "access.192.168.0.100.nip.io";
    String MY_KEYCLOAK_BASE_URL = "http://" + MY_KEYCLOAK_HOSTNAME + "/auth";

    default EntandoKeycloakServer newEntandoKeycloakServer() {
        return new EntandoKeycloakServerBuilder()
                .withNewMetadata()
                .withName(MY_KEYCLOAK)
                .withNamespace(MY_KEYCLOAK_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withDefault(true)
                .withReplicas(2)
                .withImageName("entando/entando-keycloak")
                .withIngressHostName(MY_KEYCLOAK_HOSTNAME)
                .withDbms(DbmsVendor.MYSQL)
                //                .withTlsSecretName(MY_KEYCLOAK_TLS_SECRET)
                .withEntandoImageVersion("6.0.0").endSpec()
                .build();
    }

    default EntandoClusterInfrastructure newEntandoClusterInfrastructure() {
        return new EntandoClusterInfrastructureBuilder()
                .withNewMetadata()
                .withName(MY_CLUSTER_INFRASTRUCTURE)
                .withNamespace(MY_CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.MYSQL)
                .withEntandoImageVersion("6.0.0")
                .withKeycloakSecretToUse(DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .withIngressHostName("entando-infra.192.168.0.100.nip.io")
                .withReplicas(3)
                .withDefault(true)
                .withTlsSecretName(MY_CLUSTER_INFRASTRUCTURE_TLS_SECRET)
                .endSpec()
                .build();
    }

    default EntandoApp newTestEntandoApp() {
        return new EntandoAppBuilder()
                .withNewMetadata()
                .withName(MY_APP)
                .withNamespace(MY_APP_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withStandardServerImage(JeeServer.WILDFLY)
                .withDbms(DbmsVendor.MYSQL)
                .withIngressHostName("myapp.192.168.0.100.nip.io")
                .withReplicas(1)
                .withEntandoImageVersion("6.0.0")
                .withTlsSecretName(MY_APP_TLS_SECRET)
                .withKeycloakSecretToUse(DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .endSpec()
                .build();
    }

    default EntandoPlugin buildTestEntandoPlugin() {
        return new EntandoPluginBuilder()
                .withNewMetadata()
                .withName(MY_PLUGIN)
                .withNamespace(MY_PLUGIN_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withImage("entando/myplugin")
                .withDbms(DbmsVendor.MYSQL)
                .withReplicas(2)
                .withIngressPath("/myplugin")
                .withHealthCheckPath("/actuator/health")
                .withSecurityLevel(PluginSecurityLevel.STRICT)
                .withKeycloakSecretToUse(DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .addNewRole("some-role", "role-name")
                .addNewPermission("myplugin", "plugin-admin")
                .addNewConnectionConfigName("pam-connection")
                .endSpec()
                .build();
    }

    default Secret newKeycloakAdminSecret() {
        return new SecretBuilder().withNewMetadata().withName(DEFAULT_KEYCLOAK_ADMIN_SECRET).endMetadata()
                .addToStringData(KubeUtils.USERNAME_KEY, MY_KEYCLOAK_ADMIN_USERNAME)
                .addToStringData(KubeUtils.PASSSWORD_KEY, MY_KEYCLOAK_ADMIN_PASSWORD)
                .addToStringData(KubeUtils.URL_KEY, MY_KEYCLOAK_BASE_URL)
                .build();
    }

    default Secret buildInfrastructureSecret() {
        return new SecretBuilder().withNewMetadata().withName(EntandoOperatorConfig.getEntandoInfrastructureSecretName()).endMetadata()
                .addToStringData("entandoK8SServiceClientId", "asdf")
                .addToStringData("entandoK8SServiceInternalUrl", "http://som.com/asdf")
                .addToStringData("entandoK8SServiceExternalUrl", "http://som.com/asdf")
                .addToStringData("userManagementInternalUrl", "http://som.com/asdf")
                .addToStringData("userManagementExternalUrl", "http://som.com/asdf")
                .build();
    }

    @SuppressWarnings("squid:S2068")
    default Secret buildKeycloakSecret() {
        return new SecretBuilder()
                .withNewMetadata()
                .withName(EntandoOperatorConfig.getDefaultKeycloakSecretName())
                .endMetadata()
                .addToStringData(KubeUtils.URL_KEY, MY_KEYCLOAK_BASE_URL)
                .addToStringData(KubeUtils.PASSSWORD_KEY, MY_KEYCLOAK_ADMIN_PASSWORD)
                .addToStringData(KubeUtils.USERNAME_KEY, MY_KEYCLOAK_ADMIN_USERNAME)
                .build();
    }

    default PodStatus succeededPodStatus() {
        return new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
    }

    default PodStatus readyPodStatus() {
        return new PodStatusBuilder().withPhase(RUNNING_PHASE)
                .addNewContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endContainerStatus()
                .addNewInitContainerStatus().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                .endState().endInitContainerStatus()
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
    }

}
