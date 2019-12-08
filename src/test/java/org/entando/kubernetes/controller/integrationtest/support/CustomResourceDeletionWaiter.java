package org.entando.kubernetes.controller.integrationtest.support;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomResourceDeletionWaiter {

    private RawCustomResourceOperationsImpl operation;
    private String name;
    private String namespace;

    public CustomResourceDeletionWaiter(KubernetesClient client, String kind) {
        this.operation = client.customResource(new CustomResourceDefinitionContext.Builder()
                .withPlural(kind.toLowerCase() + "s")
                .withVersion(TestFixturePreparation.CURRENT_ENTANDO_RESOURCE_VERSION)
                .withGroup("entando.org")
                .withScope("Namespaced")
                .withName(kind)
                .build());
    }

    public CustomResourceDeletionWaiter named(String name) {
        this.name = name;
        return this;
    }

    public CustomResourceDeletionWaiter fromNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public void waitingAtMost(long duration, TimeUnit timeUnit) {
        if (name == null) {
            if (((List) this.operation.list(namespace).get("items")).size() > 0) {
                this.operation.delete(namespace);
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> ((List) this.operation.list(namespace).get("items")).isEmpty());
            }
        } else {
            if (this.operation.get(namespace, name) != null) {
                this.operation.delete(namespace, name);
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> this.operation.get(namespace, name) == null);
            }
        }
    }
}
