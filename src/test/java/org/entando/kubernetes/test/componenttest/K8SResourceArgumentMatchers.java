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

package org.entando.kubernetes.test.componenttest;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.extensions.IngressStatus;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.WebServerStatus;
import org.mockito.ArgumentMatcher;

public interface K8SResourceArgumentMatchers {

    default ArgumentMatcher<AbstractServerStatus> matchesServiceStatus(ServiceStatus serviceStatus) {
        return s -> s != null && s.getServiceStatus() == serviceStatus;
    }

    default ArgumentMatcher<AbstractServerStatus> matchesDeploymentStatus(DeploymentStatus status) {
        return s -> s != null && s.getDeploymentStatus() == status;
    }

    default ArgumentMatcher<AbstractServerStatus> containsThePersistentVolumeClaimStatus(
            PersistentVolumeClaimStatus status) {
        return s -> s != null && s.getPersistentVolumeClaimStatuses() != null && s.getPersistentVolumeClaimStatuses()
                .stream().anyMatch(pvcStatus -> pvcStatus == status);
    }

    default ArgumentMatcher<AbstractServerStatus> matchesIngressStatus(IngressStatus ingressStatus) {
        return s -> s instanceof WebServerStatus && ((WebServerStatus) s).getIngressStatus() == ingressStatus;
    }

    default <T extends HasMetadata> ArgumentMatcher<T> matchesName(String name) {
        return hasMetadata -> hasMetadata.getMetadata().getName().equals(name);
    }
}
