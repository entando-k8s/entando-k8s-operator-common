package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeletionWaiter<
        R extends HasMetadata,
        L extends KubernetesResourceList<R>,
        D extends Doneable<R>,
        O extends Resource<R, D>> {

    private MixedOperation<R, L, D, O> operation;
    private String name;
    private String namespace;
    private Map<String, String> labels = new HashMap<>();
    private boolean deleteIndividually;

    public DeletionWaiter(MixedOperation<R, L, D, O> operation) {
        this.operation = operation;
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
        if (labels.isEmpty()) {
            if (name == null) {
                if (this.operation.inNamespace(namespace).list().getItems().size() > 0) {
                    if (deleteIndividually) {
                        List<R> items = this.operation.inNamespace(namespace).list().getItems();
                        for (R item : items) {
                            name = item.getMetadata().getName();
                            waitingAtMost(duration, timeUnit);
                        }
                    } else {
                        this.operation.inNamespace(namespace).delete();
                        await().atMost(duration, timeUnit)
                                .ignoreExceptions()
                                .until(() -> this.operation.inNamespace(namespace).list().getItems().isEmpty());
                    }
                }
            } else {
                if (this.operation.inNamespace(namespace).withName(name).get() != null) {
                    this.operation.inNamespace(namespace).withName(name).cascading(true).delete();
                    await().atMost(duration, timeUnit)
                            .ignoreExceptions()
                            .until(() -> this.operation.inNamespace(namespace).withName(name).get() == null);
                }
            }
        } else {
            if (this.operation.inNamespace(namespace).withLabels(labels).list().getItems().size() > 0) {
                this.operation.inNamespace(namespace).withLabels(labels).delete();
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> this.operation.inNamespace(namespace).withLabels(labels).list().getItems().isEmpty());
            }
        }
    }

    public DeletionWaiter<R, L, D, O> individually() {
        this.deleteIndividually = true;
        return this;
    }
}
