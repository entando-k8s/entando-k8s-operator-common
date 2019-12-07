package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.entando.kubernetes.controller.common.ControllerExecutor;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ControllerContainerStartingListener<
        R extends EntandoCustomResource,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>
        > {

    protected final CustomResourceOperationsImpl<R, L, D> operations;
    private boolean shouldListen = true;

    public ControllerContainerStartingListener(CustomResourceOperationsImpl<R, L, D> operations) {
        this.operations = operations;
    }

    public void stopListening() {
        shouldListen = false;
    }

    public void listen(String namespace, ControllerExecutor executor) {
        operations.inNamespace(namespace).watch(new Watcher<R>() {
            @Override
            public void eventReceived(Action action, R resource) {
                if (shouldListen && action == Action.ADDED) {
                    try {
                        System.out.println("!!!!!!!On " + resource.getKind() + " add!!!!!!!!!");
                        executor.startControllerFor(action, resource);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                cause.printStackTrace();
            }
        });
    }

}
