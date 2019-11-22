package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.controller.spi.Ingressing;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.controller.spi.IngressingPathOnPort;
import org.entando.kubernetes.model.EntandoCustomResource;

public class IngressPathCreator {

    private final EntandoCustomResource entandoCustomResource;

    public IngressPathCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public List<HTTPIngressPath> buildPaths(IngressingDeployable<?> ingressingDeployable, Service service) {
        return ingressingDeployable.getIngressingContainers().stream().map(o -> newHttpPath(o, service)).collect(Collectors
                .toList());
    }

    public Ingress addMissingHttpPaths(IngressClient ingressClient, Ingressing<?> ingressingDeployable, Ingress ingress, Service service) {
        List<IngressingPathOnPort> ingressingContainers = ingressingDeployable.getIngressingContainers().stream()
                .filter(path -> this.httpPathIsAbsent(ingress, path))
                .collect(Collectors.toList());
        for (IngressingPathOnPort ingressingContainer : ingressingContainers) {
            ingressClient.addHttpPath(ingress, newHttpPath(ingressingContainer, service), Collections
                    .singletonMap(entandoCustomResource.getMetadata().getName() + "-path", ingressingContainer.getWebContextPath()));

        }
        return ingressClient.loadIngress(ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
    }

    private HTTPIngressPath newHttpPath(IngressingPathOnPort ingressingPathOnPort, Service service) {
        return new HTTPIngressPathBuilder()
                .withPath(ingressingPathOnPort.getWebContextPath())
                .withNewBackend()
                .withServiceName(service.getMetadata().getName())
                .withNewServicePort(ingressingPathOnPort.getPort())
                .endBackend()
                .build();
    }

    private boolean httpPathIsAbsent(Ingress ingress, IngressingPathOnPort ingressingContainer) {
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                .noneMatch(httpIngressPath -> httpIngressPath.getPath().equals(ingressingContainer.getWebContextPath()));
    }

}
