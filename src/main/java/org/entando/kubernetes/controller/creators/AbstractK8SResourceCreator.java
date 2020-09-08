/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class AbstractK8SResourceCreator {

    protected final EntandoBaseCustomResource<?> entandoCustomResource;

    public AbstractK8SResourceCreator(EntandoBaseCustomResource<?> entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    protected String resolveName(String nameQualifier, String suffix) {
        return entandoCustomResource.getMetadata().getName() + "-" + nameQualifier + suffix;
    }

    protected ObjectMeta fromCustomResource(boolean ownedByCustomResource, String name, String nameQualifier) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource(nameQualifier));
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(KubeUtils.buildOwnerReference(this.entandoCustomResource));
        }
        return metaBuilder.build();
    }

    protected ObjectMeta fromCustomResource(boolean ownedByCustomResource, String name) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource());
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(KubeUtils.buildOwnerReference(this.entandoCustomResource));
        }
        return metaBuilder.build();
    }

    protected Map<String, String> labelsFromResource(String nameQualifier) {
        Map<String, String> labels = new ConcurrentHashMap<>();
        labels.put(DeployCommand.DEPLOYMENT_LABEL_NAME, resolveName(nameQualifier, ""));
        resourceKindLabels(labels);
        return labels;
    }

    protected Map<String, String> labelsFromResource() {
        Map<String, String> labels = new ConcurrentHashMap<>();
        resourceKindLabels(labels);
        return labels;
    }

    private void resourceKindLabels(Map<String, String> labels) {
        labels.put(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName());
        labels.put(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, entandoCustomResource.getKind());
    }
}
