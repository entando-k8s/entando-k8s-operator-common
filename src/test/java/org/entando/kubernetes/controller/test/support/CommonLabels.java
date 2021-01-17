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

package org.entando.kubernetes.controller.test.support;

import java.util.HashMap;
import java.util.Map;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentSpec;

public interface CommonLabels {

    default <S extends EntandoDeploymentSpec> Map<String, String> dbPreparationJobLabels(EntandoBaseCustomResource<S> resource,
            String deploymentQualifier) {
        Map<String, String> labelsFromResource = labelsFromResource(resource);
        labelsFromResource.put(KubeUtils.JOB_KIND_LABEL_NAME, KubeUtils.JOB_KIND_DB_PREPARATION);
        labelsFromResource.put(KubeUtils.DEPLOYMENT_QUALIFIER_LABEL_NAME, deploymentQualifier);
        return labelsFromResource;
    }

    default <S extends EntandoDeploymentSpec> Map<String, String> labelsFromResource(EntandoBaseCustomResource<S> resource) {
        Map<String, String> labels = new HashMap<>();
        labels.put(resource.getKind(), resource.getMetadata().getName());
        labels.put(KubeUtils.ENTANDO_RESOURCE_KIND_LABEL_NAME, resource.getKind());
        return labels;
    }
}
