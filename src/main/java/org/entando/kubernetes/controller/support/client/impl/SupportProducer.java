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

package org.entando.kubernetes.controller.support.client.impl;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import okhttp3.logging.HttpLoggingInterceptor;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.SerializingCapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializingDeploymentProcessor;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;

@ApplicationScoped
public class SupportProducer {

    private KubernetesClient kubernetesClient;
    private SimpleK8SClient<EntandoResourceClient> simpleK8SClient;

    @Produces
    public KubernetesClient getKubernetesClient() {
        if (kubernetesClient == null) {
            ConfigBuilder configBuilder = new ConfigBuilder().withTrustCerts(true).withRequestTimeout(30000).withConnectionTimeout(30000);
            kubernetesClient = new DefaultKubernetesClient(configBuilder.build());
            ((HttpClientAware) kubernetesClient).getHttpClient().networkInterceptors().removeIf(HttpLoggingInterceptor.class::isInstance);
        }
        return kubernetesClient;
    }

    @Produces
    public KubernetesClientForControllers entandoResourceClient() {
        return getSimpleKubernetesClient().entandoResources();
    }

    @Produces
    public DeploymentProcessor deploymentProcessor() {
        return new SerializingDeploymentProcessor(entandoResourceClient(),
                new InProcessCommandStream(getSimpleKubernetesClient(), keycloakClient()));
    }

    @Produces
    public CapabilityProvider capabilityProvider() {
        return new SerializingCapabilityProvider(entandoResourceClient(),
                new InProcessCommandStream(getSimpleKubernetesClient(), new DefaultKeycloakClient()));
    }

    @Produces
    public SimpleKeycloakClient keycloakClient() {
        return new DefaultKeycloakClient();
    }

    private SimpleK8SClient<EntandoResourceClient> getSimpleKubernetesClient() {
        if (simpleK8SClient == null) {
            simpleK8SClient = new DefaultSimpleK8SClient(getKubernetesClient());
        }
        return simpleK8SClient;
    }

}
