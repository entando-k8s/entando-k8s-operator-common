package org.entando.kubernetes.controller.integrationtest.podwaiters;

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

    public AbstractPodWaiter throwException(Class<? extends RuntimeException> exceptionClass) {
        this.exceptionClass = exceptionClass;
        return (AbstractPodWaiter) this;
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
