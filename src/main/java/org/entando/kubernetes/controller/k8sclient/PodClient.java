package org.entando.kubernetes.controller.k8sclient;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Watchable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.entando.kubernetes.client.PodWatcher;

public interface PodClient extends PodWaitingClient {

    Pod start(Pod pod);

    Pod waitForPod(String namespace, String labelName, String labelValue);

    Pod loadPod(String namespace, String labelName, String labelValue);

    Pod runToCompletion(Pod pod);

}
