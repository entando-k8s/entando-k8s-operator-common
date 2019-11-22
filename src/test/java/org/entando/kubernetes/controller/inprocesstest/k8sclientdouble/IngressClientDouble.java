package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class IngressClientDouble extends AbstractK8SClientDouble implements IngressClient {

    public IngressClientDouble(
            Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putIngress(ingress.getMetadata().getName(), ingress);
        return ingress;
    }

    @Override
    public Ingress loadIngress(String namespace, String name) {
        if (namespace == null) {
            return null;
        }
        return getNamespace(namespace).getIngress(name);
    }

    @Override
    public Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations) {
        if (ingress == null) {
            return null;
        }
        ingress.getSpec().getRules().get(0).getHttp().getPaths().add(httpIngressPath);
        ingress.getMetadata().getAnnotations().putAll(annotations);
        return ingress;
    }

    @Override
    public String getMasterUrlHost() {
        return "somehost.com";
    }
}
