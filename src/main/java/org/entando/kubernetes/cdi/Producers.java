package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import io.fabric8.openshift.client.OpenShiftClient;
import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.validation.Validator;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.entando.kubernetes.controller.EntandoOperatorConfig;

@ApplicationScoped
public class Producers {

    private Producers() {
        super();
    }

    @Produces
    @ApplicationScoped
    public static final OkHttpClient produceOkHttpClient(final Config config) {
        return HttpClientUtils.createHttpClient(config);
    }

    public static final void destroyOkHttpClient(@Disposes final OkHttpClient client) throws IOException {
        if (client != null) {
            // See https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
            final Dispatcher dispatcher = client.dispatcher();
            if (dispatcher != null) {
                final ExecutorService executorService = dispatcher.executorService();
                if (executorService != null) {
                    executorService.shutdownNow();
                    // Stop accepting new connection requests.
                }
                // Cancel any connections in progress.
                dispatcher.cancelAll();
            }
            final ConnectionPool connectionPool = client.connectionPool();
            if (connectionPool != null) {
                // Boot all connections out of the pool.
                connectionPool.evictAll();
            }
            final Closeable cache = client.cache();
            if (cache != null) {
                cache.close();
            }
        }
    }

    @Produces
    @ApplicationScoped
    public static final ConfigBuilder produceConfigBuilder(final Instance<Validator> validatorInstance) {
        return new ConfigBuilder();
    }

    @Produces
    @ApplicationScoped
    public static final Config produceConfig(ConfigBuilder configBuilder) {
        Objects.requireNonNull(configBuilder);
        configBuilder.withTrustCerts(true).withConnectionTimeout(30000).withRequestTimeout(30000);
        EntandoOperatorConfig.getOperatorNamespaceOverride().ifPresent(configBuilder::withNamespace);
        return configBuilder.build();
    }

    @Produces
    @ApplicationScoped
    public static final OpenShiftClient produceOpenshiftClient(AutoAdaptableKubernetesClient kc) {
        return kc.adapt(OpenShiftClient.class);
    }

    @Produces
    @ApplicationScoped
    public static final AutoAdaptableKubernetesClient produceKubernetesClient(OkHttpClient httpClient, Config config) {
        return new AutoAdaptableKubernetesClient(httpClient, config);
    }

    public static final void disposeKubernetesClient(@Disposes final DefaultKubernetesClient client) {
        // We deliberately do NOT call close() on the supplied client,
        // because it is possible to construct a DefaultKubernetesClient
        // with an OkHttpClient passed in from the outside (as is done in
        // this class).  Consequently it is bad form for a
        // DefaultKubernetesClient to close() it!
    }

}
