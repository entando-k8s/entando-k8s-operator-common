package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import javax.enterprise.inject.Produces;
import org.entando.kubernetes.controller.EntandoOperatorConfig;

public class KubernetesClientProducer {

    @Produces
    public KubernetesClient produce() {
        ConfigBuilder configBuilder = new ConfigBuilder().withTrustCerts(true).withRequestTimeout(30000).withConnectionTimeout(30000);
        EntandoOperatorConfig.getOperatorNamespaceOverride().ifPresent(configBuilder::withNamespace);
        return new DefaultKubernetesClient(configBuilder.build());
    }
}
