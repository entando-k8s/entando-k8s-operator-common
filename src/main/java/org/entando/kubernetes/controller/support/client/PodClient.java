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

package org.entando.kubernetes.controller.support.client;

import io.fabric8.kubernetes.api.model.Pod;
import java.util.Map;

public interface PodClient extends WaitingClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod loadPod(String namespace, Map<String, String> labels);

    Pod runToCompletion(Pod pod);

    void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels);

    void removeAndWait(String namespace, Map<String, String> labels);

    void deletePod(Pod pod);
}
