package org.entando.kubernetes.controller.integrationtest.podwaiters;

import io.fabric8.kubernetes.client.KubernetesClientException;
import java.time.Duration;
import org.entando.kubernetes.controller.PodResult.State;

/**
 * Limit the use of this class to integration tests. It does thread locking (Object.wait) which is not ideal for server side applications.
 * We also have not been able to determine how to stop the Mutex from listening to events, which is likely to constitute a memory leak. In
 * the long run, we should be looking at an entirely async design
 */
public class ServicePodWaiter extends AbstractPodWaiter<ServicePodWaiter> {

    private Duration readinessTimeout = Duration.ofSeconds(10);

    @Override
    public boolean wasSuccessful() {
        return super.wasSuccessful() && this.state == State.READY;
    }

    @Override
    protected boolean isRunning() {
        return this.state == State.RUNNING;
    }

    @Override
    protected boolean applyRunningTimeout() {
        boolean finish = false;
        timedOut = System.currentTimeMillis() >= this.readinessTimeout.toMillis() + containerStartedRunning;
        if (failReason != null || timedOut) {
            finish = true;
        } else {
            long remainingReadinessTime =
                    this.readinessTimeout.toMillis() - (System.currentTimeMillis() - containerStartedRunning);
            waitAndCatch(Duration.ofMillis(Math.max(0L, remainingReadinessTime)));
        }
        return finish;
    }

    public ServicePodWaiter limitReadinessTo(Duration readinessTimeout) {
        this.readinessTimeout = readinessTimeout;
        return this;
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        //Do nothing
    }
}
