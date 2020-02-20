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

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import javax.enterprise.inject.Produces;
import org.entando.kubernetes.controller.EntandoOperatorConfig;

public class KubernetesClientProducer {

    @Produces
    public KubernetesClient produce() {
        ConfigBuilder configBuilder = new ConfigBuilder().withTrustCerts(true).withRequestTimeout(30000).withConnectionTimeout(30000);
        EntandoOperatorConfig.getOperatorNamespaceToObserve().ifPresent(configBuilder::withNamespace);
        return new DefaultKubernetesClient(configBuilder.build());
    }
}
