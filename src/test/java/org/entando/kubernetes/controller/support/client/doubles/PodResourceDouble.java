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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.internal.HasMetadataOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.PodOperationContext;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.PodOperationsImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PodResourceDouble extends PodOperationsImpl {

    private final KubernetesClient client;

    public PodResourceDouble(String namespace) {
        this(new PodOperationContext(), HasMetadataOperationsImpl.defaultContext(new KubernetesClientBuilder().build()).withNamespace(namespace));
    }

    public PodResourceDouble(PodOperationContext podOperationContext, io.fabric8.kubernetes.client.dsl.internal.OperationContext operationContext) {
        super(podOperationContext, operationContext);
        this.client = (KubernetesClient) operationContext.getClient();
    }

    @Override
    public PodOperationsImpl inContainer(String containerId) {
        return new PodResourceDouble(getContext().withContainerId(containerId), context);
    }

    @Override
    public PodOperationsImpl readingInput(InputStream in) {
        return new PodResourceDouble(getContext().withIn(in), context);
    }

    @Override
    public PodOperationsImpl writingOutput(OutputStream out) {
        return new PodResourceDouble(getContext().toBuilder().output(new PodOperationContext.StreamContext(out)).build(), context);
    }

    @Override
    public PodOperationsImpl redirectingOutput() {
        return new PodResourceDouble(getContext().toBuilder().output(new PodOperationContext.StreamContext()).build(), context);
    }

    @Override
    public PodOperationsImpl writingError(OutputStream err) {
        return new PodResourceDouble(getContext().toBuilder().error(new PodOperationContext.StreamContext(err)).build(), context);
    }

    @Override
    public PodOperationsImpl redirectingError() {
        return new PodResourceDouble(getContext().toBuilder().error(new PodOperationContext.StreamContext()).build(), context);
    }

    @Override
    public PodOperationsImpl writingErrorChannel(OutputStream errChannel) {
        return new PodResourceDouble(getContext().toBuilder().errorChannel(new PodOperationContext.StreamContext(errChannel)).build(), context);
    }

    @Override
    public PodOperationsImpl redirectingErrorChannel() {
        return new PodResourceDouble(getContext().toBuilder().errorChannel(new PodOperationContext.StreamContext()).build(), context);
    }

    @Override
    public PodOperationsImpl usingListener(ExecListener execListener) {
        return new PodResourceDouble(getContext().withExecListener(execListener), context);
    }

    @Override
    public PodOperationsImpl withTTY() {
        return new PodResourceDouble(getContext().withTty(true), context);
    }

    @Override
    @SuppressWarnings("java:S2925")
    public ExecWatch exec(String... command) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            getContext().getExecListener().onClose(0, "Success");
        }).start();
        return new ExecWatchDouble(Arrays.asList(command));
    }

    public class ExecWatchDouble implements ExecWatch {

        private final List<String> commands;

        public ExecWatchDouble(List<String> asList) {
            this.commands = asList;
        }

        @Override
        public OutputStream getInput() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getOutput() {
            return new ByteArrayInputStream(String.join("\n", commands).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getError() {
            return null;
        }

        @Override
        public InputStream getErrorChannel() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public void resize(int cols, int rows) {

        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> exitCode() {
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
    }
}
