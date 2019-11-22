package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.k8sclient.ServiceClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.Ingressing;
import org.entando.kubernetes.controller.spi.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.ServiceResult;
import org.entando.kubernetes.model.EntandoCustomResource;

public class ServiceCreator extends AbstractK8SResourceCreator {

    private Service primaryService;

    public ServiceCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public ServiceCreator(EntandoCustomResource entandoCustomResource, Service primaryService) {
        super(entandoCustomResource);
        this.primaryService = primaryService;
    }

    private Service newService(Deployable<?> deployable) {
        ObjectMeta objectMeta = fromCustomResource(true, resolveName(deployable.getNameQualifier(), "-service"),
                deployable.getNameQualifier());
        return new ServiceBuilder()
                .withMetadata(objectMeta)
                .withNewSpec()
                .withSelector(labelsFromResource(deployable.getNameQualifier()))
                .withType("ClusterIP")
                .withPorts(buildPorts(deployable))
                .endSpec()
                .build();
    }

    private List<ServicePort> buildPorts(Deployable<?> deployable) {
        return deployable.getContainers().stream().filter(ServiceBackingContainer.class::isInstance)
                .map(ServiceBackingContainer.class::cast)
                .map(this::newServicePort).collect(Collectors.toList());
    }

    private ServicePort newServicePort(ServiceBackingContainer deployableContainer) {
        return new ServicePortBuilder()
                .withName(deployableContainer.getNameQualifier() + "-port")
                .withPort(deployableContainer.getPort())
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(deployableContainer.getPort()))
                .build();
    }

    public <T extends ServiceResult> void createService(ServiceClient services, Deployable<T> deployable) {
        primaryService = services.createService(entandoCustomResource, newService(deployable));
    }

    public ServiceStatus reloadPrimaryService(ServiceClient services) {
        if (this.primaryService == null) {
            return null;
        }
        this.primaryService = services.loadService(entandoCustomResource, primaryService.getMetadata().getName());
        return this.primaryService.getStatus();
    }

    public Service newDelegatingService(ServiceClient services, Ingressing<?> ingressingDeployable) {
        ObjectMeta metaData = new ObjectMetaBuilder()
                .withLabels(labelsFromResource(ingressingDeployable.getNameQualifier()))
                .withName(ingressingDeployable.getIngressName() + "-to-" + primaryService.getMetadata().getName())
                .withNamespace(ingressingDeployable.getIngressNamespace())
                .withOwnerReferences(KubeUtils.buildOwnerReference(this.entandoCustomResource)).build();
        Service delegatingService = services.createService(entandoCustomResource, new ServiceBuilder()
                .withMetadata(metaData)
                .withNewSpec()
                .withPorts(new ArrayList<>(primaryService.getSpec().getPorts()))
                .endSpec()
                .build());
        services.createEndpoints(entandoCustomResource, new EndpointsBuilder()
                .withMetadata(metaData)
                .addNewSubset()
                .addNewAddress().withIp(primaryService.getSpec().getClusterIP()).endAddress()
                .withPorts(toEndpointPorts(primaryService.getSpec().getPorts()))
                .endSubset()
                .build());
        return delegatingService;
    }

    private List<EndpointPort> toEndpointPorts(List<ServicePort> ports) {
        return ports.stream().map(servicePort -> new EndpointPort(servicePort.getName(), servicePort.getPort(), servicePort.getProtocol()))
                .collect(Collectors.toList());

    }

    public Service getService() {
        return primaryService;
    }
}
