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

package org.entando.kubernetes.controller.spi.common;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.entando.kubernetes.model.common.EntandoControllerFailure;

public class ExceptionUtils {

    public static EntandoControllerFailure failureOf(HasMetadata r, Exception e) {
        final StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        if (r == null) {
            return new EntandoControllerFailure(null, null, e.getMessage(), stringWriter.toString());
        } else {
            final String failedObjectName = r.getMetadata().getNamespace() + "/" + r.getMetadata().getName();
            return new EntandoControllerFailure(r.getKind(), failedObjectName, e.getMessage(), stringWriter.toString());
        }
    }

    public static EntandoControllerFailure failureOf(EntandoControllerException e) {
        return failureOf(e.getKubernetesResource(), e);
    }

    public static <T> T retry(Supplier<T> supplier, Predicate<RuntimeException> ignoreExceptionWhen, int count) {
        for (int i = 0; i < count - 1; i++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                if (!ignoreExceptionWhen.test(e)) {
                    throw e;
                }
                long actualCount = i + 1L;
                final long duration = actualCount * actualCount;
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(duration));
            }
        }
        return supplier.get();
    }

    public interface IoVulnerable<T> {

        T invoke() throws IOException;
    }

    private ExceptionUtils() {
    }

    public static <T> T ioSafe(IoVulnerable<T> i) {
        try {
            return i.invoke();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
