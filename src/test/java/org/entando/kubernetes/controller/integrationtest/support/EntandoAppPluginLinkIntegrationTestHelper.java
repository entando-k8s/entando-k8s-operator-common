package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.entando.kubernetes.model.link.DoneableEntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLinkList;
import org.entando.kubernetes.model.link.EntandoAppPluginLinkOperationFactory;

public class EntandoAppPluginLinkIntegrationTestHelper extends
        IntegrationTestHelperBase<EntandoAppPluginLink, EntandoAppPluginLinkList, DoneableEntandoAppPluginLink> {

    public EntandoAppPluginLinkIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoAppPluginLinkOperationFactory::produceAllEntandoAppPluginLinks);
    }

}
