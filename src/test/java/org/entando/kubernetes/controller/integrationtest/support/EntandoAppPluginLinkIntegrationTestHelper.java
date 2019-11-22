package org.entando.kubernetes.controller.integrationtest.support;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.entando.kubernetes.model.link.DoneableEntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.link.EntandoAppPluginLinkList;

public class EntandoAppPluginLinkIntegrationTestHelper extends AbstractIntegrationTestHelper {

    private CustomResourceOperationsImpl<EntandoAppPluginLink, EntandoAppPluginLinkList,
            DoneableEntandoAppPluginLink> entandoPluginOperations;

    public EntandoAppPluginLinkIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client);
    }

    public CustomResourceOperationsImpl<EntandoAppPluginLink,
            EntandoAppPluginLinkList, DoneableEntandoAppPluginLink> getEntandoAppPluginLinkOperations() {
        if (entandoPluginOperations == null) {
            this.entandoPluginOperations = this.entandoPluginsInAnyNamespace();
        }
        return entandoPluginOperations;
    }

    private CustomResourceOperationsImpl<EntandoAppPluginLink, EntandoAppPluginLinkList,
            DoneableEntandoAppPluginLink> entandoPluginsInAnyNamespace() {

        CustomResourceDefinition entandoPluginCrd = client.customResourceDefinitions()
                .withName(EntandoAppPluginLink.CRD_NAME).get();
        return (CustomResourceOperationsImpl<EntandoAppPluginLink, EntandoAppPluginLinkList, DoneableEntandoAppPluginLink>) client
                .customResources(entandoPluginCrd, EntandoAppPluginLink.class, EntandoAppPluginLinkList.class,
                        DoneableEntandoAppPluginLink.class).inAnyNamespace();
    }

}
