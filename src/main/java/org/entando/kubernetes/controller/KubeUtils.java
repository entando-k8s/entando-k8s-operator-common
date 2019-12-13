package org.entando.kubernetes.controller;

import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public final class KubeUtils {

    //TODO these seem a bit lost here
    public static final String PASSSWORD_KEY = "password";//Funny name because a variable named 'PASSWORD' is considered a vulnerability
    public static final String USERNAME_KEY = "username";
    public static final String DB_JOB_LABEL_NAME = "dbJob";
    public static final String DEFAULT_SERVER_QUALIFIER = "server";
    public static final String URL_KEY = "url";
    public static final String DEFAULT_SERVICE_SUFFIX = "service";
    public static final String DEFAULT_INGRESS_SUFFIX = "ingress";
    public static final String ENTANDO_RESOURCE_ACTION = "entando.resource.action";
    public static final String ENTANDO_RESOURCE_NAMESPACE = "entando.resource.namespace";
    public static final String ENTANDO_RESOURCE_NAME = "entando.resource.name";
    public static final String ENTANDO_APP_ROLE = "entandoApp";
    public static final String ENTANDO_PLUGIN_ROLE = "entandoPlugin";
    public static final String ENTANDO_CLUSTER_ROLE = "entandoCluster";
    //TODO Find out why we only have one public client.
    public static final String PUBLIC_CLIENT_ID = "entando-web";
    public static final String OPERATOR_CLIENT_ID = "entando-k8s-operator";
    public static final String ENTANDO_KEYCLOAK_REALM = "entando";
    public static final String ENTANDO_RESOURCE_KIND_LABEL_NAME = "EntandoResourceKind";

    private static final Logger LOGGER = Logger.getLogger(KubeUtils.class.getName());

    private KubeUtils() {
    }

    public static String getKindOf(Class<? extends EntandoBaseCustomResource> c) {
        //TODO this is problematic even for Fabric8. We need to change EntandoKeycloakServer to EntandoEntandoKeycloakServer
        if (c == EntandoKeycloakServer.class) {
            return "EntandoEntandoKeycloakServer";
        }
        return c.getSimpleName();
    }

    public static EnvVarSource secretKeyRef(String secretName, String key) {
        return new EnvVarSourceBuilder().withNewSecretKeyRef(key, secretName, Boolean.FALSE).build();
    }

    public static String snakeCaseOf(String in) {
        return in.replace("-", "_").replace(".", "_");
    }

    public static Secret generateSecret(EntandoCustomResource resource, String secretName, String username) {
        String password = RandomStringUtils.randomAlphanumeric(10);
        return buildSecret(resource, secretName, username, password);
    }

    public static Secret buildSecret(EntandoCustomResource resource, String secretName, String username,
            String password) {
        return new SecretBuilder()
                .withNewMetadata().withName(secretName)
                .withOwnerReferences(buildOwnerReference(resource))
                .addToLabels(resource.getKind(), resource.getMetadata().getName())
                .endMetadata()
                .addToStringData(USERNAME_KEY, username)
                .addToStringData(PASSSWORD_KEY, password)
                .build();
    }

    public static void ready(String name) {
        try {
            FileUtils.write(new File("/tmp/" + name + ".ready"), "yes", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not create 'ready' file for {0}", name);
        }
    }

    public static OwnerReference buildOwnerReference(EntandoCustomResource entandoCustomResource) {
        return new OwnerReferenceBuilder()
                .withApiVersion(entandoCustomResource.getApiVersion())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .withKind(entandoCustomResource.getKind())
                .withName(entandoCustomResource.getMetadata().getName())
                .withUid(entandoCustomResource.getMetadata().getUid()).build();
    }

    public static String standardIngressName(EntandoCustomResource entandoCustomResource) {
        return entandoCustomResource.getMetadata().getName() + "-" + DEFAULT_INGRESS_SUFFIX;
    }
}
