package org.entando.kubernetes.controller.integrationtest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.impl.TlsHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorE2ETestConfig;
import org.entando.kubernetes.controller.integrationtest.support.IntegrationClientFactory;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.IngressingContainer;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("inter-process"), @Tag("smoke-test")})
public class KeycloakClientTest {

    public static final String KCP = "7UTcVFN0HzaPQmV4bJDE";//RandomStringUtils.randomAlphanumeric(20);

    public static final String KC_TEST_NAMESPACE = EntandoOperatorE2ETestConfig.getTestNamespaceOverride().orElse("kc-test-namespace");
    public static final String MY_REALM = "my-realm";
    public static final String MY_CLIENT = "my-client";
    public static final String EXISTING_CLIENT = "existing-client";
    public static final String EXISTING_ROLE = "existing-role";
    private static DefaultKeycloakClient keycloakClient = null;
    private final DefaultKubernetesClient client = IntegrationClientFactory.newClient();
    private final DefaultSimpleK8SClient simpleK8SClient = new DefaultSimpleK8SClient(client);
    private final KeycloakIntegrationTestHelper helper = new KeycloakIntegrationTestHelper(client) {
        protected Optional<Secret> getAdminSecret() {
            return Optional.ofNullable(
                    new SecretBuilder()
                            .addToStringData(KubeUtils.USERNAME_KEY, "test-admin")
                            .addToStringData(KubeUtils.PASSSWORD_KEY, KCP)
                            .addToStringData(KubeUtils.URL_KEY, TlsHelper.getDefaultProtocol() + "://test-kc." + domainSuffix + "/auth")
                            .build());
        }

    };
    private final String domainSuffix = helper.getDomainSuffix();
    private KeycloakServer keycloakServer = new KeycloakServerBuilder().editMetadata()
            .withName("test-kc")
            .withNamespace(KC_TEST_NAMESPACE)
            .endMetadata()
            .withNewSpec()
            .withDbms(DbmsImageVendor.NONE)
            .withIngressHostName("test-kc." + domainSuffix)
            .withReplicas(1)
            .endSpec()
            .build();

    @Test
    public void testEnsureRealm() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //When I ensure that a specific real is available
        kc.ensureRealm(MY_REALM);
        //Then an Operator Client is created under this realm
        var operatorClient = helper.findClientInRealm(MY_REALM, KubeUtils.OPERATOR_CLIENT_ID);
        assertThat(operatorClient.isPresent(), is(true));
        //With only basic functionality enabled
        assertThat(operatorClient.get().isStandardFlowEnabled(), is(false));
        assertThat(operatorClient.get().isImplicitFlowEnabled(), is(false));
        assertThat(operatorClient.get().isPublicClient(), is(false));
    }

    @Test
    public void testCreatePublicClient() {
        //Given a Keycloak Server is available and I have logged int
        var kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //When I create the public client in this realm
        kc.createPublicClient(MY_REALM, "http://test.domain.com");
        //Then a new Client should be available
        var publicClient = helper.findClientInRealm(MY_REALM, KubeUtils.PUBLIC_CLIENT_ID);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient enabled
        assertThat(publicClient.get().isPublicClient(), is(true));
        //And the correct redirectUris and origins configured
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        assertThat(publicClient.get().getWebOrigins().get(0), is("http://test.domain.com"));
    }

    @Test
    public void testPrepareClientWithPermissions() {
        //Given a Keycloak Server is available and I have logged int
        var kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //And I have created a client
        kc.prepareClientAndReturnSecret(new KeycloakClientConfig(MY_REALM, EXISTING_CLIENT, EXISTING_CLIENT)
                .withRedirectUri("http://existingclient.domain.com/*")
                .withRole(EXISTING_ROLE)
        );
        //When I create the public client in this realm
        kc.prepareClientAndReturnSecret(new KeycloakClientConfig(MY_REALM, MY_CLIENT, MY_CLIENT)
                .withRedirectUri("http://test.domain.com/*")
                .withPermission(EXISTING_CLIENT, EXISTING_ROLE)
        );
        //Then a new client should be available
        var publicClient = helper.findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient functionality disabled
        assertThat(publicClient.get().isPublicClient(), is(false));
        //With correct redirect uri
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        //With correct permissions
        var roleRepresentations = helper.retrieveServiceAccountRolesInRealm(MY_REALM, MY_CLIENT, EXISTING_CLIENT);
        assertThat(roleRepresentations.get(0).getName(), is(EXISTING_ROLE));
    }

    private DefaultKeycloakClient prepareKeycloak() {
        if (keycloakClient == null) {
            System.setProperty(EntandoOperatorConfig.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT, "true");
            helper.recreateNamespaces(KC_TEST_NAMESPACE);
            var operation = helper.getOperations().inNamespace(KC_TEST_NAMESPACE);
            keycloakServer = operation.createOrReplace(keycloakServer);
            var result = new DeployCommand<>(new TestKeycloakDeployable(keycloakServer)).execute(simpleK8SClient, Optional
                    .empty());
            simpleK8SClient.pods().waitForPod(KC_TEST_NAMESPACE, DeployCommand.DEPLOYMENT_LABEL_NAME, "test-kc-server");
            keycloakClient = new DefaultKeycloakClient();
            keycloakClient.login(TlsHelper.getDefaultProtocol() + "://test-kc." + domainSuffix + "/auth", "test-admin", KCP);
        }
        return keycloakClient;
    }

    private static class TestKeycloakDeployable implements IngressingDeployable<ServiceDeploymentResult> {

        private final List<DeployableContainer> containers = Arrays.asList(new TestKeycloakContainer());
        private final KeycloakServer keycloakServer;

        private TestKeycloakDeployable(KeycloakServer keycloakServer) {
            this.keycloakServer = keycloakServer;
        }

        @Override
        public List<DeployableContainer> getContainers() {
            return containers;
        }

        @Override
        public String getIngressName() {
            return keycloakServer.getMetadata().getName() + "-ingress";
        }

        @Override
        public String getIngressNamespace() {
            return keycloakServer.getMetadata().getNamespace();
        }

        @Override
        public String getNameQualifier() {
            return "server";
        }

        @Override
        public EntandoCustomResource getCustomResource() {
            return keycloakServer;
        }

        @Override
        public ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
            return new ServiceDeploymentResult(service, ingress);
        }

        private static class TestKeycloakContainer implements IngressingContainer {

            @Override
            public String determineImageToUse() {
                return "entando/entando-keycloak:6.0.0-SNAPSHOT";
            }

            @Override
            public String getNameQualifier() {
                return "server";
            }

            @Override
            public int getPort() {
                return 8080;
            }

            @Override
            public String getWebContextPath() {
                return "/auth";
            }

            @Override
            public void addEnvironmentVariables(List<EnvVar> vars) {
                vars.add(new EnvVar("DB_VENDOR", "h2", null));
                vars.add(new EnvVar("KEYCLOAK_USER", "test-admin", null));
                vars.add(new EnvVar("KEYCLOAK_PASSWORD", KCP, null));
            }

            @Override
            public Optional<String> getHealthCheckPath() {
                return Optional.of(getWebContextPath());
            }
        }
    }
}
