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

package org.entando.kubernetes.model;

import static java.lang.String.format;

import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.CharArrayWriter;
import java.io.PrintWriter;

public class EntandoControllerFailureBuilder {

    private String failedObjectType;
    private String failedObjectName;
    private String message;
    private String detailMessage;

    public EntandoControllerFailure build() {
        return new EntandoControllerFailure(failedObjectType, failedObjectName, message, detailMessage);
    }

    public EntandoControllerFailureBuilder withFailedObjectType(String failedObjectType) {
        this.failedObjectType = failedObjectType;
        return this;
    }

    public EntandoControllerFailureBuilder withFailedObjectName(String failedObjectName) {
        this.failedObjectName = failedObjectName;
        return this;
    }

    public EntandoControllerFailureBuilder withFailedObjectName(String namespace, String failedObjectName) {
        this.failedObjectName = format("%s/%s", namespace, failedObjectName);
        return this;
    }

    public EntandoControllerFailureBuilder withMessage(String message) {
        this.message = message;
        return this;
    }

    public EntandoControllerFailureBuilder withException(Exception exception) {
        if (exception instanceof KubernetesClientException) {
            KubernetesClientException kce = (KubernetesClientException) exception;
            if (kce.getStatus() == null) {
                withMessage(exception.getMessage());
            } else {
                withMessage(kce.getStatus().getMessage());
                if (kce.getStatus().getDetails() != null) {
                    withFailedObjectType(kce.getStatus().getDetails().getKind());
                    withFailedObjectName(kce.getStatus().getDetails().getName());
                }
            }
        } else {
            withMessage(exception.getMessage());
        }
        CharArrayWriter charArrayWriter = new CharArrayWriter();
        exception.printStackTrace(new PrintWriter(charArrayWriter));
        this.detailMessage = charArrayWriter.toString();
        return this;
    }

}
