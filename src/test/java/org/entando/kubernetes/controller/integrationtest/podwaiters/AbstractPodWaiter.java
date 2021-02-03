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

package org.entando.kubernetes.controller.integrationtest.podwaiters;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;

/**
 * Limit the use of this class to integration tests. It does thread locking (Object.wait) which is not ideal for server side applications.
 * We also have not been able to determine how to stop the Mutex from listening to events, which is likely to constitute a memory leak. In
 * the long run, we should be looking at an entirely async design
 */

public abstract class AbstractPodWaiter<T extends AbstractPodWaiter> extends AbstractK8SWaiter implements Watcher<Pod> {

    protected Long containerStartedRunning;
    protected boolean finished;
    protected State previousState = State.CREATING;
    protected State state = State.CREATING;
    protected Duration containerCreationTimeout = Duration.ofSeconds(600);//10 Minutes to allow for images to be pulled
    private Long containerCreationStarted;

    public void waitOn(PodResource<Pod, DoneablePod> pod) {
        synchronized (this) {
            determineState(pod.get());
            try (Watch ignored = pod.watch(this)) {
                while (this.state == State.CREATING) {
                    logContainerCreationStarted();
                    if (applyContainerCreationTimeout()) {
                        break;
                    }
                }
                while (isRunning()) {
                    logContainerStarted();
                    if (applyRunningTimeout()) {
                        break;
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            } finally {
                finish(pod.get());
                maybeThrowException();
            }
        }
    }

    private void finish(Pod pod) {
        logStatus("Finished: " + pod.getMetadata().getName());
        finished = true;
    }

    private void maybeThrowException() {
        if (exceptionClass != null && !wasSuccessful()) {
            try {
                throw exceptionClass.getConstructor(String.class)
                        .newInstance(getFailReason() + " during " + state.name());
            } catch (NoSuchMethodException | InvocationTargetException
                    | IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void logContainerStarted() {
        if (containerStartedRunning == null) {
            containerStartedRunning = System.currentTimeMillis();
        }
    }

    private void logContainerCreationStarted() {
        if (containerCreationStarted == null) {
            containerCreationStarted = System.currentTimeMillis();
        }
    }

    private boolean applyContainerCreationTimeout() {
        timedOut = System.currentTimeMillis() >= this.containerCreationTimeout.toMillis() + containerCreationStarted;
        if (timedOut || failReason != null) {
            return true;
        } else {
            long remainingContainerCreationTime =
                    this.containerCreationTimeout.toMillis() - (System.currentTimeMillis() - containerCreationStarted);
            waitAndCatch(Duration.ofMillis(Math.max(0L, remainingContainerCreationTime)));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public T limitContainerCreationTo(Duration containerCreationTimeout) {
        this.containerCreationTimeout = containerCreationTimeout;
        return (T) this;
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        synchronized (this) {
            logStatus(action.name() + ":" + pod.getStatus());
            if (!finished) {
                determineState(pod);
                if (previousState != state || failReason != null || timedOut) {
                    notifyAll();
                }
            }
        }
    }

    private void determineState(Pod pod) {
        PodResult podResult = new PodResult() {
            @Override
            //TODO rather override hasFailed and getFailReason. In fact, abstract a common superclass rather
            protected boolean didSomeContainersNotRun(Pod pod) {
                return false;//because we use a timeout on a websocket
            }
        };
        podResult.init(pod);
        this.previousState = state;
        this.state = podResult.getState();
        this.failReason = podResult.getFailReason();
    }

}
