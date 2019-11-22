package org.entando.kubernetes.controller.k8sclient;

import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface IngressClient {

    Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress);

    Ingress loadIngress(String namespace, String name);

    Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations);

    String getMasterUrlHost();
}
