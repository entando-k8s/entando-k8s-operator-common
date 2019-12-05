package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.Pod;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface PodClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod runToCompletion(EntandoCustomResource resource, Pod pod);

}
