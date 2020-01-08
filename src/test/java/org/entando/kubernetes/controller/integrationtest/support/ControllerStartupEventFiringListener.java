package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ControllerStartupEventFiringListener<
        R extends EntandoCustomResource,
        L extends CustomResourceList<R>,
        D extends DoneableEntandoCustomResource<D, R>
        > {

    private final CustomResourceOperationsImpl<R, L, D> operations;
    private boolean shouldListen = true;
    private Watch watch;

    public ControllerStartupEventFiringListener(CustomResourceOperationsImpl<R, L, D> operations) {
        this.operations = operations;
    }

    public void stopListening() {
        shouldListen = false;
        if (watch != null) {
            watch.close();
            watch = null;
        }
    }

    public void listen(String namespace, OnStartupMethod onStartupMethod) {
        this.watch = operations.inNamespace(namespace).watch(new Watcher<R>() {
            @Override
            public void eventReceived(Action action, R resource) {
                if (shouldListen && action == Action.ADDED) {
                    try {
                        System.out.println("!!!!!!!On " + resource.getKind() + " add!!!!!!!!!");
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, action.name());
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, resource.getMetadata().getNamespace());
                        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, resource.getMetadata().getName());
                        onStartupMethod.onStartup(new StartupEvent());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                Optional.ofNullable(cause).ifPresent(Throwable::printStackTrace);
            }
        });
    }

    public interface OnStartupMethod {

        void onStartup(StartupEvent event);
    }
}
