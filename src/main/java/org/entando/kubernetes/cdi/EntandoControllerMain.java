package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.controller.impl.TlsHelper;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabase;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

public final class EntandoControllerMain {

    private static final Logger LOGGER = Logger.getLogger(EntandoControllerMain.class.getName());
    private static WeldContainer container;
    private static boolean stopMainThread = false;
    private static List<Runnable> cdiContainerCleanup = new ArrayList<>();

    private EntandoControllerMain() {
    }

    public static void registerCdiContainerCleanup(Runnable r) {
        cdiContainerCleanup.add(r);
    }

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        TlsHelper.getInstance().init();
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#EntandoApp", EntandoApp.class);
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#EntandoPlugin", EntandoPlugin.class);
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#EntandoClusterInfrastructure", EntandoClusterInfrastructure.class);
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#ExternalDatabase", ExternalDatabase.class);
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#EntandoKeycloakServer", KeycloakServer.class);
        KubernetesDeserializer.registerCustomKind("entando.org/v1alpha1#EntandoAppPluginLink", EntandoAppPluginLink.class);
        Weld weld = new Weld();
        container = weld.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(EntandoControllerMain::stop));
        new Thread(() -> {
            while (!stopMainThread) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    public static void stop() {
        try {
            Producers.destroyOkHttpClient(container.select(OkHttpClient.class).get());
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Exception closing OkHttpClient: ", e);
        }
        cdiContainerCleanup.forEach(runnable -> {
            try {
                runnable.run();
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Exception cleaning up with " + runnable.getClass().getName(), e);
            }
        });
        if (container.isRunning()) {
            container.shutdown();
        }
        stopMainThread = true;
    }

    public static WeldContainer getContainer() {
        return container;
    }
}
