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

import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;

public class SerializingDeploymentProcessor implements DeploymentProcessor {

    private final KubernetesClientForControllers entandoResourceClient;
    private final CommandStream commandStream;
    private Deployable<?> deployable;

    public SerializingDeploymentProcessor(KubernetesClientForControllers entandoResourceClient, CommandStream commandStream) {
        this.entandoResourceClient = entandoResourceClient;
        this.commandStream = commandStream;
    }

    @Override
    public <T extends ServiceDeploymentResult<T>> T processDeployable(Deployable<T> deployable, int timeoutSeconds)
            throws TimeoutException {
        this.deployable = deployable;
        String result = commandStream.process(
                SupportedCommand.PROCESS_DEPLOYABLE,
                SerializationHelper.serialize(deployable),
                timeoutSeconds);
        SerializableDeploymentResult<?> serializedResult = DeserializationHelper.deserialize(entandoResourceClient, result);
        if (serializedResult.getStatus().hasFailed()) {
            throw new EntandoControllerException("Creation of Kubernetes resources has failed");
        }
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
