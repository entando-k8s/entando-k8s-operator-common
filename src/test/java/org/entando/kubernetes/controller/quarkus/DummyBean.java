package org.entando.kubernetes.controller.quarkus;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.quarkus.runtime.StartupEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import org.entando.kubernetes.client.DefaultIngressClient;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.creators.IngressCreator;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.IngressingContainer;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.keycloakserver.DoneableEntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerList;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerOperationFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * This class is used only to build a native app with the goal of testing if all the dependencies are building correctly. It can be moved to
 * /src/main/java and then mvn clean package -Pnative will build a sample app.
 */
public class DummyBean {

    private static final String KCP = "7UTcVFN0HzaPQmV4bJDE";//RandomStringUtils.randomAlphanumeric(20);

    private static final String KC_TEST_NAMESPACE = "kc-test-namespace";
    private static final String MY_REALM = "my-realm";
    private static final String MY_CLIENT = "my-client";
    private static final String EXISTING_CLIENT = "existing-client";
    private static final String EXISTING_ROLE = "existing-role";
    private final KubernetesClient kubernetesClient;
    private final DefaultSimpleK8SClient simpleK8SClient;
    private final String domainSuffix;
    private final CustomResourceOperationsImpl<EntandoKeycloakServer, EntandoKeycloakServerList, DoneableEntandoKeycloakServer> operations;
    private DefaultKeycloakClient keycloakClient;
    private EntandoKeycloakServer keycloakServer;

    @Inject
    public DummyBean(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.simpleK8SClient = new DefaultSimpleK8SClient(kubernetesClient);
        this.domainSuffix = IngressCreator.determineRoutingSuffix(DefaultIngressClient.resolveMasterHostname(kubernetesClient));
        this.operations = EntandoKeycloakServerOperationFactory.produceAllEntandoKeycloakServers(kubernetesClient);
        this.keycloakServer = new EntandoKeycloakServerBuilder().editMetadata()
                .withName("test-kc")
                .withNamespace(KC_TEST_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsImageVendor.NONE)
                .withIngressHostName("test-kc." + domainSuffix)
                .withReplicas(1)
                .endSpec()
                .build();
        loadproviders();
    }

    public static void main(String[] args) {
        new DummyBean(new DefaultKubernetesClient()).onStart(null);
    }

    public void testEnsureRealm() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //When I ensure that a specific real is available
        kc.ensureRealm(MY_REALM);
        //Then an Operator Client is created under this realm
        Optional<ClientRepresentation> operatorClient = findClientInRealm(MY_REALM, KubeUtils.OPERATOR_CLIENT_ID);
        assertThat(operatorClient.isPresent(), is(true));
        //With only basic functionality enabled
        assertThat(operatorClient.get().isStandardFlowEnabled(), is(false));
        assertThat(operatorClient.get().isImplicitFlowEnabled(), is(false));
        assertThat(operatorClient.get().isPublicClient(), is(false));
    }

    public void testCreatePublicClient() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
        //And  I have ensured that a specific real is available
        kc.ensureRealm(MY_REALM);
        //When I create the public client in this realm
        kc.createPublicClient(MY_REALM, "http://test.domain.com");
        //Then a new Client should be available
        Optional<ClientRepresentation> publicClient = findClientInRealm(MY_REALM, KubeUtils.PUBLIC_CLIENT_ID);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient enabled
        assertThat(publicClient.get().isPublicClient(), is(true));
        //And the correct redirectUris and origins configured
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        assertThat(publicClient.get().getWebOrigins().get(0), is("http://test.domain.com"));
    }

    public void testPrepareClientWithPermissions() {
        //Given a Keycloak Server is available and I have logged int
        DefaultKeycloakClient kc = prepareKeycloak();
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
        Optional<ClientRepresentation> publicClient = findClientInRealm(MY_REALM, MY_CLIENT);
        assertThat(publicClient.isPresent(), is(true));
        //With publicClient functionality disabled
        assertThat(publicClient.get().isPublicClient(), is(false));
        //With correct redirect uri
        assertThat(publicClient.get().getRedirectUris().get(0), is("http://test.domain.com/*"));
        //With correct permissions
        List<RoleRepresentation> roleRepresentations = retrieveServiceAccountRolesInRealm(MY_REALM, MY_CLIENT, EXISTING_CLIENT);
        assertThat(roleRepresentations.get(0).getName(), is(EXISTING_ROLE));
    }

    private DefaultKeycloakClient prepareKeycloak() {
        if (keycloakClient == null) {
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT.getJvmSystemProperty(), "true");
            keycloakClient = new DefaultKeycloakClient();
            keycloakClient
                    .login(TlsHelper.getDefaultProtocol() + "://test-kc." + domainSuffix + "/auth",
                            "test-admin", KCP);
        }
        return keycloakClient;
    }

    public void onStart(/*@Observes*/ StartupEvent e) {
        try {
            testEnsureRealm();
            testCreatePublicClient();
            testPrepareClientWithPermissions();
        } finally {
            new Thread(() -> System.exit(0)).start();
        }

    }

    void assertThat(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError();
        }
    }

    Object is(Object expected) {
        return expected;
    }

    public Optional<ClientRepresentation> findClientInRealm(String realmName, String clientId) {
        try {
            RealmResource realm = getKeycloak().realm(realmName);
            ClientsResource clients = realm.clients();
            return clients.findByClientId(clientId).stream().findFirst();
        } catch (ClientErrorException e) {
            if (e.getResponse() != null && e.getResponse().getEntity() != null) {
                System.out.println(e.toString());
                System.out.println(e.getResponse().getEntity());
            }
            throw e;
        }
    }

    private Keycloak getKeycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(TlsHelper.getDefaultProtocol() + "://test-kc." + domainSuffix
                        + "/auth")
                .grantType(OAuth2Constants.PASSWORD)
                .realm("master")
                .clientId("admin-cli")
                .username("test-admin")
                .password(KCP)
                .build();
    }

    public List<RoleRepresentation> retrieveServiceAccountRolesInRealm(String realmName, String serviceAccountClientId,
            String targetClientId) {
        RealmResource realm = getKeycloak().realm(realmName);
        ClientsResource clients = realm.clients();
        ClientRepresentation serviceAccountClient = clients.findByClientId(serviceAccountClientId).get(0);
        ClientRepresentation targetClient = clients.findByClientId(targetClientId).get(0);
        UserRepresentation serviceAccountUser = clients.get(serviceAccountClient.getId()).getServiceAccountUser();
        return realm.users().get(serviceAccountUser.getId()).roles().clientLevel(targetClient.getId()).listAll();
    }

    private void loadproviders() {
        //This method is just intended to make RestEasy fail earlier to save time
        ResteasyProviderFactory instance = ResteasyProviderFactory.newInstance();
        instance
                .registerProviderInstance(new org.jboss.resteasy.client.jaxrs.internal.CompletionStageRxInvokerProvider());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.providers.jackson.UnrecognizedPropertyExceptionHandler());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.providers.jackson.PatchMethodFilter());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.DataSourceProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.DefaultTextPlain());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.DefaultNumberWriter());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.DocumentProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.DefaultBooleanWriter());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.StringTextStar());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.SourceProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.InputStreamProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.ReaderProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.ByteArrayProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.JaxrsFormProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.CompletionStageProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.ReactiveStreamProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.FileProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.FileRangeWriter());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.StreamingOutputProvider());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.interceptors.CacheControlFeature());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.interceptors.encoding.ClientContentEncodingAnnotationFeature());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.interceptors.encoding.ServerContentEncodingAnnotationFeature());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.interceptors.encoding.MessageSanitizerContainerResponseFilter());
        instance.registerProviderInstance(new org.jboss.resteasy.plugins.providers.sse.SseEventProvider());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.providers.sse.SseEventOutputProvider());
        instance
                .registerProviderInstance(new org.jboss.resteasy.plugins.providers.sse.SseEventSinkInterceptor());
        ResteasyProviderFactory.setInstance(instance);
    }

    private static class TestKeycloakDeployable implements IngressingDeployable<ServiceDeploymentResult> {

        private final List<DeployableContainer> containers = Arrays.asList(new TestKeycloakContainer());
        private final EntandoKeycloakServer keycloakServer;

        private TestKeycloakDeployable(EntandoKeycloakServer keycloakServer) {
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
