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

package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressStatus;
import org.mockito.stubbing.Answer;

public interface K8SStatusBasedAnswers {

    default Answer<Ingress> respondWithIngressStatusForPath(IngressStatus ingressStatus,
            String path) {
        return invocationOnMock -> {
            Ingress ingress = (Ingress) invocationOnMock.callRealMethod();
            if (ingress != null && ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                    .anyMatch(httpIngressPath -> path.equals(httpIngressPath.getPath()))) {
                ingress.setStatus(ingressStatus);
            }
            return ingress;
        };
    }

    default Answer<Deployment> respondWithDeploymentStatus(DeploymentStatus deploymentStatus) {
        return invocationOnMock -> {
            Deployment deployment = (Deployment) invocationOnMock.callRealMethod();
            deployment.setStatus(deploymentStatus);
            return deployment;
        };
    }

    default Answer<PersistentVolumeClaim> respondWithPersistentVolumeClaimStatus(PersistentVolumeClaimStatus status) {
        return invocationOnMock -> {
            PersistentVolumeClaim pvc = (PersistentVolumeClaim) invocationOnMock.callRealMethod();
            pvc.setStatus(status);
            return pvc;
        };
    }

    default Answer<Service> respondWithServiceStatus(ServiceStatus dbServiceStatus) {
        return invocationOnMock -> {
            Service service = (Service) invocationOnMock.callRealMethod();
            if (service != null) {
                service.setStatus(dbServiceStatus);
            }
            return service;
        };
    }
}
