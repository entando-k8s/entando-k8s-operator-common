package org.entando.kubernetes.controller;

import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.awaitility.core.ConditionFactory;

@Deprecated
/**
 * @deprecated Use Awaitility
 */
public class Wait {

    private final int waitDuration;
    private TimeUnit waitTimeUnit;

    public Wait(int number) {
        this.waitDuration = number;
    }

    public static Wait waitFor(int number) {
        return new Wait(number);
    }

    public Wait milliseconds() {
        waitTimeUnit = TimeUnit.MILLISECONDS;
        return this;
    }

    public Wait seconds() {
        waitTimeUnit = TimeUnit.SECONDS;
        return this;
    }

    public void orUntil(BooleanSupplier condition) {
        ConditionFactory atMost = await().atMost(waitDuration, waitTimeUnit);
        if (waitDuration <= 100 && waitTimeUnit == TimeUnit.MILLISECONDS) {
            atMost.pollInterval(10, TimeUnit.MILLISECONDS);
        }
        atMost.until(() -> condition.getAsBoolean());
    }

}
