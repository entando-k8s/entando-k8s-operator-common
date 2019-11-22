package org.entando.kubernetes.controller.inprocesstest;

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
