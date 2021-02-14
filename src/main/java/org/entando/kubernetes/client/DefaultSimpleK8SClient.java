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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;

public class DefaultSimpleK8SClient implements SimpleK8SClient<EntandoResourceClient> {

    private final KubernetesClient kubernetesClient;
    private PodClient podClient;
    private SecretClient secretClient;
    private EntandoResourceClient entandoResourceClient;

    public DefaultSimpleK8SClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public PodClient pods() {
        if (this.podClient == null) {
            this.podClient = new DefaultPodClient(kubernetesClient);
        }
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        if (this.secretClient == null) {
            this.secretClient = new DefaultSecretClient(kubernetesClient);
        }
        return this.secretClient;
    }

    @Override
    public EntandoResourceClient entandoResources() {
        if (this.entandoResourceClient == null) {

            this.entandoResourceClient = new DefaultEntandoResourceClient(kubernetesClient);
        }
        return this.entandoResourceClient;
    }
}
