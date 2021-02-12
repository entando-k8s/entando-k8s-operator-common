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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

public class EntandoExecListener implements ExecListener {

    private final Object mutex;
    private final int timeoutSeconds;
    private final long start = System.currentTimeMillis();
    private boolean failed = false;
    boolean shouldStillWait = true;
    private ByteArrayOutputStream outWriter = new ByteArrayOutputStream();
    private Execable<String, ExecWatch> execable;

    public EntandoExecListener(Object mutex, int timeoutSeconds) {
        this.mutex = mutex;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void onOpen(Response response) {
        //no implementation required
    }

    @Override
    public void onFailure(Throwable t, Response response) {
        shouldStillWait = false;
        failed = true;
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    @Override
    public void onClose(int code, String reason) {
        shouldStillWait = false;
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    public boolean shouldStillWait() {
        if (System.currentTimeMillis() - start > timeoutSeconds * 1000L) {
            failed = true;
            shouldStillWait = false;
            return false;
        }
        return shouldStillWait;
    }

    public OutputStream getOutWriter() {
        return outWriter;
    }

    public List<String> getOutput() throws IOException {
        return IOUtils.readLines(new ByteArrayInputStream(outWriter.toByteArray()), StandardCharsets.UTF_8);
    }

    public boolean hasFailed() {
        return failed;
    }

    public void setExecable(Execable<String, ExecWatch> execable) {
        this.execable = execable;
    }

    public Execable<String, ExecWatch> getExecable() {
        return execable;
    }
}
