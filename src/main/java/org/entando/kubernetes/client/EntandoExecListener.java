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
import okhttp3.Response;

public class EntandoExecListener implements ExecListener {

    private long start = System.currentTimeMillis();
    private boolean failed = false;
    boolean shouldStillWait = true;
    private Object mutex;

    public EntandoExecListener(Object mutex) {
        this.mutex = mutex;
    }

    @Override
    public void onOpen(Response response) {
        System.out.println("response = " + response);
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
        if (System.currentTimeMillis() - start > 20000) {
            failed = true;
            shouldStillWait = false;
            return false;
        }
        return shouldStillWait;
    }

    public boolean hasFailed() {
        return failed;
    }
}
