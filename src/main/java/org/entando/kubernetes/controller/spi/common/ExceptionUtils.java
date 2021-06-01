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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ExceptionUtils {

    public static EntandoControllerFailure failureOf(EntandoCustomResource r, Exception e) {
        final StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        final String failedObjectName = r.getMetadata().getNamespace() + "/" + r.getMetadata().getName();
        return new EntandoControllerFailure(r.getKind(), failedObjectName, e.getMessage(), stringWriter.toString());
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
