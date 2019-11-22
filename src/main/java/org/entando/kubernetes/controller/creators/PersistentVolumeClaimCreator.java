package org.entando.kubernetes.controller.creators;

import static java.util.Collections.singletonMap;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.k8sclient.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.model.EntandoCustomResource;

public class PersistentVolumeClaimCreator extends AbstractK8SResourceCreator {

    private List<PersistentVolumeClaim> persistentVolumeClaims;

    public PersistentVolumeClaimCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public boolean needsPersistentVolumeClaaims(Deployable<?> deployable) {
        return deployable.getContainers().stream()
                .anyMatch(PersistentVolumeAware.class::isInstance);
    }

    public void createPersistentVolumeClaimsFor(PersistentVolumeClaimClient k8sClient, Deployable<?> deployable) {
        this.persistentVolumeClaims = deployable.getContainers().stream()
                .filter(PersistentVolumeAware.class::isInstance)
                .map(deployableContainer -> k8sClient
                        .createPersistentVolumeClaim(entandoCustomResource, newPersistentVolumeClaim(deployable, deployableContainer)))
                .collect(Collectors.toList());

    }

    public List<PersistentVolumeClaimStatus> reloadPersistentVolumeClaims(PersistentVolumeClaimClient k8sClient) {
        return Optional.ofNullable(persistentVolumeClaims).orElse(Collections.emptyList()).stream()
                .map(persistentVolumeClaim -> k8sClient.loadPersistentVolumeClaim(entandoCustomResource,
                        persistentVolumeClaim.getMetadata().getName()).getStatus())
                .collect(Collectors.toList());
    }

    private PersistentVolumeClaim newPersistentVolumeClaim(Deployable<?> deployable, DeployableContainer container) {

        return new PersistentVolumeClaimBuilder()
                .withMetadata(fromCustomResource(true, resolveName(container.getNameQualifier(), "-pvc"),
                        deployable.getNameQualifier()))
                .withNewSpec().withAccessModes("ReadWriteOnce")
                .withNewResources()
                .withRequests(singletonMap("storage", new Quantity("2Gi")))
                .endResources().endSpec()
                .build();
    }

}
