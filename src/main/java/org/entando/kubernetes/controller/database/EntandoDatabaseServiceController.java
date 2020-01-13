package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.common.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class EntandoDatabaseServiceController extends AbstractDbAwareController<EntandoDatabaseService> {

    /**
     * Constructor for integration tests where we would need to override the auto exit behaviour.
     */
    public EntandoDatabaseServiceController(KubernetesClient client) {
        super(client, false);
    }

    /**
     * Constructor for in process tests where we may want to mock the clients out and would not want to exit.
     */
    public EntandoDatabaseServiceController(SimpleK8SClient<?> k8sClient) {
        super(k8sClient, null);
    }

    public void processEvent(Action action, EntandoDatabaseService db) {
        super.processAction(action, db);
    }

    @Override
    protected void processAddition(EntandoDatabaseService entandoDatabaseService) {
        new CreateExternalServiceCommand(entandoDatabaseService).execute(super.k8sClient);
    }
}
