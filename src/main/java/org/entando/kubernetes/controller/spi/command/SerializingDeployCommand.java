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

package org.entando.kubernetes.controller.spi.command;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;

public class SerializingDeployCommand<T extends ServiceDeploymentResult<T>> {

    private CommandStream commandStream;

    private final Deployable<T> deployable;
    private final SerializationHelper<T> helper;

    public SerializingDeployCommand(KubernetesClient kubernetesClient, Deployable<T> deployable, SimpleK8SClient<?> client) {
        this.deployable = deployable;
        this.helper = new SerializationHelper<>(kubernetesClient);
        this.commandStream = new InProcessCommandStream(helper, client, new DefaultKeycloakClient());
    }

    public T execute() {
        String result = commandStream.process(helper.serialize(deployable));
        SerializableDeploymentResult<?> serializedResult = helper.deserialize(result);
        return this.deployable.createResult(serializedResult.getDeployment(), serializedResult.getService(), serializedResult.getIngress(),
                serializedResult.getPod())
                .withStatus(serializedResult.getStatus());
    }

    public Deployable<DefaultSerializableDeploymentResult> getSerializedDeployable() {
        return serializeThenDeserialize(deployable);
    }

    private <S> S serializeThenDeserialize(Object deployable) {
        final String json = helper.serialize(deployable);
        return helper.deserialize(json);
    }

}
