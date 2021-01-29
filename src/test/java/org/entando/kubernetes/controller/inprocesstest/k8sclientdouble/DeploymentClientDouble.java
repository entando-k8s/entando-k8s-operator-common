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

package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Map;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DeploymentClientDouble extends AbstractK8SClientDouble implements DeploymentClient {

    public DeploymentClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace,
            Deployment deployment) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putDeployment(deployment.getMetadata().getName(), deployment);
        Pod pod = createPodFrom(deployment);
        getNamespace(peerInNamespace).putPod(pod);
        return deployment;
    }

    private Pod createPodFrom(Deployment deployment) {
        return new PodBuilder().withNewMetadataLike(deployment.getMetadata()).endMetadata().build();
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getDeployment(name);
    }
}
