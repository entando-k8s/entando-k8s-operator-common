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

import org.entando.kubernetes.controller.spi.client.CustomResourceClient;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;

public class SerializingDeployCommand {

    private final CustomResourceClient entandoResourceClient;
    private final CommandStream commandStream;
    private Deployable<?> deployable;

    public SerializingDeployCommand(CustomResourceClient entandoResourceClient, CommandStream commandStream) {
        this.entandoResourceClient = entandoResourceClient;
        this.commandStream = commandStream;
    }

    public <T extends ServiceDeploymentResult<T>> T processDeployable(Deployable<T> deployable) {
        this.deployable = deployable;
        String result = commandStream.process(SerializationHelper.serialize(deployable));
        SerializableDeploymentResult<?> serializedResult = DeserializationHelper.deserialize(entandoResourceClient, result);
        return deployable.createResult(serializedResult.getDeployment(), serializedResult.getService(), serializedResult.getIngress(),
                serializedResult.getPod())
                .withStatus(serializedResult.getStatus());
    }

    public Deployable<DefaultSerializableDeploymentResult> getSerializedDeployable() {
        return serializeThenDeserialize(deployable);
    }

    private <S> S serializeThenDeserialize(Object deployable) {
        final String json = SerializationHelper.serialize(deployable);
        return DeserializationHelper.deserialize(entandoResourceClient, json);
    }

}
