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

package org.entando.kubernetes.controller.support.client;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.entando.kubernetes.client.EntandoExecListener;

public interface PodClient extends PodWaitingClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    default Pod loadPod(String namespace, String... labelNamesAndValues) {
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < labelNamesAndValues.length; i += 2) {
            labels.put(labelNamesAndValues[i], labelNamesAndValues[i + 1]);
        }
        return loadPod(namespace, labels);
    }

    Pod loadPod(String namespace, Map<String, String> labels);

    Pod runToCompletion(Pod pod);

    EntandoExecListener executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands);

    @SuppressWarnings({"java:S106"})
    default EntandoExecListener executeAndWait(PodResource<Pod, DoneablePod> podResource, String containerName, int timeoutSeconds,
            String... script) {
        StringBuilder sb = new StringBuilder();
        for (String s : script) {
            sb.append(s);
            sb.append('\n');
        }
        sb.append("exit 0\n");
        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        try {
            Object mutex = new Object();
            synchronized (mutex) {
                EntandoExecListener listener = new EntandoExecListener(mutex, timeoutSeconds);
                if (ENQUEUE_POD_WATCH_HOLDERS.get()) {
                    getExecListenerHolder().add(listener);//because it should never be full during tests. fail early.
                }
                final Execable<String, ExecWatch> execable = podResource.inContainer(containerName)
                        .readingInput(in)
                        .writingOutput(listener.getOutWriter())
                        .redirectingError()
                        .withTTY()
                        .usingListener(listener);
                listener.setExecable(execable);
                execable.exec();

                while (listener.shouldStillWait()) {
                    mutex.wait(1000);
                }
                if (listener.hasFailed()) {
                    throw new IllegalStateException(format("Command did not meet the wait condition within 20 seconds: %s", sb.toString()));
                }
                return listener;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * A getter for the an AtomicReference to the most recently constructed ExecListener for testing purposes.
     */
    BlockingQueue<EntandoExecListener> getExecListenerHolder();

    void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels);

    void removeAndWait(String namespace, Map<String, String> labels);

    void deletePod(Pod pod);
}
