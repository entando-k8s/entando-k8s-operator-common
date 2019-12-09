package org.entando.kubernetes.controller.integrationtest.support;

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.Scaleable;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeletionWaiter<
        R extends HasMetadata,
        L extends KubernetesResourceList<R>,
        D extends Doneable<R>,
        O extends Resource<R, D>> {

    private static final Logger LOGGER = Logger.getLogger(DeletionWaiter.class.getName());
    private MixedOperation<R, L, D, O> operation;
    private String name;
    private String namespace;
    private Map<String, String> labels = new HashMap<>();

    public DeletionWaiter(MixedOperation<R, L, D, O> operation) {
        this.operation = operation;
    }

    public static <R extends HasMetadata,
            L extends KubernetesResourceList<R>,
            D extends Doneable<R>,
            O extends Resource<R, D>> DeletionWaiter<R, L, D, O> delete(MixedOperation<R, L, D, O> operation) {
        return new DeletionWaiter<>(operation);
    }

    public DeletionWaiter<R, L, D, O> named(String name) {
        this.name = name;
        return this;
    }

    public DeletionWaiter<R, L, D, O> withLabel(String labelName, String labelValue) {
        labels.put(labelName, labelValue);
        return this;
    }

    public DeletionWaiter<R, L, D, O> withLabel(String labelName) {
        labels.put(labelName, null);
        return this;
    }

    public DeletionWaiter<R, L, D, O> fromNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public void waitingAtMost(long duration, TimeUnit timeUnit) {
        if (name == null) {
            if (labels.isEmpty()) {
                await().atMost(duration, timeUnit).ignoreExceptions().until(() -> {
                    deleteIndividually(duration, timeUnit, this.operation.inNamespace(namespace).list());
                    return this.operation.inNamespace(namespace).list().getItems().isEmpty();

                });
            } else {
                await().atMost(duration, timeUnit).ignoreExceptions().until(() -> {
                    deleteIndividually(duration, timeUnit, this.operation.inNamespace(namespace).withLabels(labels).list());
                    return this.operation.inNamespace(namespace).withLabels(labels).list().getItems().isEmpty();

                });
            }
        } else {
            deleteSingleItem(duration, timeUnit);
        }
    }

    protected void deleteSingleItem(long duration, TimeUnit timeUnit) {
        await().atMost(duration, timeUnit)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        if (operation instanceof Scaleable) {
                            LOGGER.log(Level.WARNING,
                                    (format("Deleting %s  %s/%s to zero", ((OperationSupport) operation).getResourceT(), namespace, name)));
                            ((Scaleable<R>) operation.inNamespace(namespace).withName(name)).scale(0, true);
                        }
                        LOGGER.log(Level.WARNING,
                                (format("Deleting %s  %s/%s", ((OperationSupport) operation).getResourceT(), namespace, name)));
                        this.operation.inNamespace(namespace).withName(name).cascading(true).withGracePeriod(0).delete();
                    } catch (KubernetesClientException e) {
                        LOGGER.log(Level.WARNING, format("Deletion of %s/%s failed.", namespace, name), e);
                    }
                    return this.operation.inNamespace(namespace).withName(name).fromServer().get() == null;
                });
    }

    protected void deleteIndividually(long duration, TimeUnit timeUnit, L list) {
        for (R item : list.getItems()) {
            name = item.getMetadata().getName();
            deleteSingleItem(duration, timeUnit);
        }
    }

}
