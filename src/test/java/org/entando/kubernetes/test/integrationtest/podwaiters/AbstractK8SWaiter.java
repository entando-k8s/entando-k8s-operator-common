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

package org.entando.kubernetes.test.integrationtest.podwaiters;

import java.time.Duration;

public abstract class AbstractK8SWaiter {

    protected boolean timedOut;
    protected String failReason;
    protected Class<? extends RuntimeException> exceptionClass;

    public String getFailReason() {
        if (timedOut) {
            return "TimedOut";
        }
        return failReason;

    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractPodWaiter<?>> AbstractPodWaiter<T> throwException(Class<? extends RuntimeException> exceptionClass) {
        this.exceptionClass = exceptionClass;
        return (AbstractPodWaiter<T>) this;
    }

    protected void logStatus(String x) {
        System.out.println(x);
    }

    public boolean wasSuccessful() {
        return failReason == null && !timedOut;
    }

    protected void waitAndCatch(Duration timeout) {
        try {
            this.wait(timeout.toMillis());
        } catch (InterruptedException e) {
            logStatus("Interrupted");
        }
    }

    protected abstract boolean isRunning();

    protected abstract boolean applyRunningTimeout();
}
