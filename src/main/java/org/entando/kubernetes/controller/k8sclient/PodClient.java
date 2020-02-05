package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.Pod;

public interface PodClient extends PodWaitingClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod loadPod(String namespace, String labelName, String labelValue);

    Pod runToCompletion(Pod pod);

}
