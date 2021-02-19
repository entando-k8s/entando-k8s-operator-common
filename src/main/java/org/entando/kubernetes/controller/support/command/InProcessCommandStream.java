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

package org.entando.kubernetes.controller.support.command;

import org.entando.kubernetes.controller.spi.command.CommandStream;
import org.entando.kubernetes.controller.spi.command.DefaultSerializableDeploymentResult;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;

public class InProcessCommandStream implements CommandStream {

    private final SerializationHelper<?> helper;
    private final SimpleK8SClient<?> client;
    private final SimpleKeycloakClient keycloakClient;

    public InProcessCommandStream(
            SerializationHelper<?> helper,
            SimpleK8SClient<?> client, SimpleKeycloakClient keycloakClient) {
        this.helper = helper;
        this.client = client;
        this.keycloakClient = keycloakClient;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public String process(String deployable) {
        DeployCommand<? extends ServiceDeploymentResult> command = new DeployCommand(helper.deserialize(deployable));
        final ServiceDeploymentResult result = command.execute(client, keycloakClient);
        DefaultSerializableDeploymentResult serializableDeploymentResult = null;
        if (result instanceof ExposedDeploymentResult) {
            serializableDeploymentResult = new DefaultSerializableDeploymentResult(null, result.getPod(),
                    result.getService(), ((ExposedDeploymentResult) result).getIngress());

        } else {
            serializableDeploymentResult = new DefaultSerializableDeploymentResult(null, result.getPod(),
                    result.getService(), null);
        }

        return helper.serialize(serializableDeploymentResult);
    }
}
