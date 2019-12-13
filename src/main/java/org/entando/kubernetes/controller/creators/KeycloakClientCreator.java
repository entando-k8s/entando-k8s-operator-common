package org.entando.kubernetes.controller.creators;

import static org.entando.kubernetes.controller.creators.IngressCreator.getIngressServerUrl;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.KeycloakAware;
import org.entando.kubernetes.controller.spi.PublicIngressingDeployable;
import org.entando.kubernetes.model.EntandoCustomResource;

public class KeycloakClientCreator {

    public static final String CLIENT_SECRET_KEY = "clientSecret";
    public static final String CLIENT_ID_KEY = "clientId";
    private final EntandoCustomResource entandoCustomResource;

    public KeycloakClientCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public static String keycloakClientSecret(KeycloakClientConfig keycloakConfig) {
        return keycloakConfig.getClientId() + "-secret";
    }

    public boolean requiresKeycloakClients(Deployable<?> deployable) {
        return deployable instanceof PublicIngressingDeployable
                || deployable.getContainers().stream().anyMatch(KeycloakAware.class::isInstance);
    }

    public void createKeycloakClients(SecretClient secrets, SimpleKeycloakClient keycloak, Deployable<?> deployable,
            Optional<Ingress> ingress) {
        login(keycloak, deployable);
        if (deployable instanceof PublicIngressingDeployable) {
            //Create a single public keycloak
            keycloak.createPublicClient(((PublicIngressingDeployable<?>) deployable).getPublicKeycloakClientId(), getIngressServerUrl(
                    ingress.orElseThrow(IllegalStateException::new)));

        }
        deployable.getContainers().stream()
                .filter(KeycloakAware.class::isInstance)
                .map(KeycloakAware.class::cast)
                .forEach(keycloakAware -> createClient(secrets, keycloak, keycloakAware, ingress));
    }

    private void login(SimpleKeycloakClient client, Deployable<?> deployable) {
        KeycloakConnectionConfig keycloakConnectionConfig;
        if (deployable instanceof PublicIngressingDeployable) {
            keycloakConnectionConfig = ((PublicIngressingDeployable) deployable).getKeycloakDeploymentResult();
        } else {
            keycloakConnectionConfig = deployable.getContainers().stream()
                    .filter(KeycloakAware.class::isInstance)
                    .map(KeycloakAware.class::cast)
                    .map(KeycloakAware::getKeycloakDeploymentResult)
                    .findAny()
                    .orElseThrow(IllegalArgumentException::new);
        }
        client.login(keycloakConnectionConfig.getBaseUrl(), keycloakConnectionConfig.getUsername(),
                keycloakConnectionConfig.getPassword());
    }

    private void createClient(SecretClient secrets, SimpleKeycloakClient client, KeycloakAware container, Optional<Ingress> ingress) {
        KeycloakClientConfig keycloakConfig = container.getKeycloakConnectionConfig();
        KeycloakClientConfig keycloakClientConfig = container.getKeycloakConnectionConfig();
        if (ingress.isPresent()) {
            keycloakClientConfig = keycloakClientConfig
                    .withRedirectUri(getIngressServerUrl(ingress.get()) + container.getWebContextPath() + "/*");
            if (ingress.get().getSpec().getTls().size() == 1) {
                //Also support redirecting to http for http services that don't have knowledge that they are exposed as https
                keycloakClientConfig = keycloakClientConfig.withRedirectUri(
                        "http://" + ingress.get().getSpec().getRules().get(0).getHost() + container.getWebContextPath() + "/*");
            }
        }
        String keycloakClientSecret = client.prepareClientAndReturnSecret(keycloakClientConfig);
        String secretName = keycloakClientSecret(keycloakConfig);
        if (secrets.loadSecret(entandoCustomResource, secretName) == null) {
            secrets.createSecretIfAbsent(entandoCustomResource, new SecretBuilder()
                    .withNewMetadata()
                    .withOwnerReferences(KubeUtils.buildOwnerReference(entandoCustomResource))
                    .withName(secretName)
                    .endMetadata()
                    .addToStringData(CLIENT_ID_KEY, keycloakConfig.getClientId())
                    .addToStringData(CLIENT_SECRET_KEY, keycloakClientSecret)
                    .build());
        }
    }

}
