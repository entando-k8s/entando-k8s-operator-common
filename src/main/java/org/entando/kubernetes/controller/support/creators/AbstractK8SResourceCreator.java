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

package org.entando.kubernetes.controller.support.creators;

import com.google.common.base.Strings;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.Map;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class AbstractK8SResourceCreator {

    protected final EntandoCustomResource entandoCustomResource;

    public AbstractK8SResourceCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    protected String generateName(String nameQualifier, String suffix) {

        StringBuilder sb = new StringBuilder(entandoCustomResource.getMetadata().getName());
        if (!Strings.isNullOrEmpty(nameQualifier)) {
            sb.append("-").append(nameQualifier);
        }

        String completeSuffix = "";
        if (! Strings.isNullOrEmpty(suffix)) {
            completeSuffix = "-" + suffix;
        }
        int maxLength = NameUtils.GENERIC_K8S_MAX_LENGTH - completeSuffix.length();
        if (sb.length() > maxLength) {
            sb.setLength(maxLength);    // only if it is longer, otherwise \x00 will be added to match the required length
        }

        if (! Strings.isNullOrEmpty(completeSuffix)) {
            sb.append(completeSuffix);
        }
        return sb.toString();
    }

    protected ObjectMeta fromCustomResource(boolean ownedByCustomResource, String name, String nameQualifier) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource(nameQualifier));
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(ResourceUtils.buildOwnerReference(this.entandoCustomResource));
        }
        return metaBuilder.build();
    }

    protected ObjectMeta fromCustomResource(String name) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource()).addToOwnerReferences(ResourceUtils.buildOwnerReference(this.entandoCustomResource))
                .build();
    }

    protected Map<String, String> labelsFromResource(String nameQualifier) {
        Map<String, String> labels = labelsFromResource();
        labels.put(LabelNames.DEPLOYMENT.getName(), NameUtils.shortenLabelToMaxLength(generateName(nameQualifier, null)));
        return labels;
    }

    protected Map<String, String> labelsFromResource() {
        return ResourceUtils.labelsFromResource(this.entandoCustomResource);
    }

}
