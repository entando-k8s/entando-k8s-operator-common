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

package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressStatus;
import io.fabric8.kubernetes.api.model.extensions.IngressTLS;
import io.fabric8.kubernetes.api.model.extensions.IngressTLSBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.controller.spi.Ingressing;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.HasIngress;

public class IngressCreator<S extends EntandoIngressingDeploymentSpec> extends AbstractK8SResourceCreator<S> {

    private final IngressPathCreator ingressPathCreator;
    private Ingress ingress;

    public IngressCreator(EntandoBaseCustomResource<S> entandoCustomResource) {
        super(entandoCustomResource);
        this.ingressPathCreator = new IngressPathCreator(entandoCustomResource);
    }

    public static String getIngressServerUrl(Ingress ingress) {
        return (ingress.getSpec().getTls().isEmpty() ? "http" : "https") + "://" + ingress.getSpec().getRules().get(0)
                .getHost();
    }

    public static String determineRoutingSuffix(String masterNodeHost) {
        return EntandoOperatorConfig.getDefaultRoutingSuffix()
                .orElse(determineViableRoutingSuffix(masterNodeHost));
    }

    private static String determineViableRoutingSuffix(String masterNodeHost) {
        if (isTopLevelDomain(masterNodeHost)) {
            return masterNodeHost;
        }
        return masterNodeHost + ".nip.io";
    }

    private static boolean isTopLevelDomain(String host) {
        try {
            //loaded from https://data.iana.org/TLD/tlds-alpha-by-domain.txt
            try (InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("top-level-domains.txt");
                    BufferedReader r = new BufferedReader(new InputStreamReader(stream))) {

                String line = r.readLine();
                while (line != null) {
                    if (host.toLowerCase(Locale.getDefault()).endsWith("." + line.toLowerCase(Locale.getDefault()))) {
                        return true;
                    }
                    line = r.readLine();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return false;
    }

    public boolean requiresDelegatingService(Service service, Ingressing<?> ingressingContainer) {
        return !service.getMetadata().getNamespace().equals(ingressingContainer.getIngressNamespace());
    }

    public void createIngress(IngressClient ingressClient, IngressingDeployable<?, S> ingressingDeployable, Service service) {
        this.ingress = ingressClient.loadIngress(ingressingDeployable.getIngressNamespace(), ingressingDeployable.getIngressName());
        if (this.ingress == null) {
            Ingress newIngress = newIngress(ingressClient, ingressPathCreator.buildPaths(ingressingDeployable, service));
            this.ingress = ingressClient.createIngress(entandoCustomResource, newIngress);
        } else {
            ingressPathCreator.addMissingHttpPaths(ingressClient, ingressingDeployable, ingress, service);
        }
    }

    public IngressStatus reloadIngress(IngressClient ingresses) {
        if (this.ingress == null) {
            return null;
        }
        this.ingress = ingresses.loadIngress(ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
        return this.ingress.getStatus();
    }

    public Ingress getIngress() {
        return ingress;
    }

    private Ingress newIngress(IngressClient ingressClient, List<HTTPIngressPath> paths) {
        return new IngressBuilder()
                .withNewMetadata()
                .withAnnotations(forNginxIngress())
                .withName(KubeUtils.standardIngressName(entandoCustomResource))
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .addToLabels(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName())
                .withOwnerReferences(KubeUtils.buildOwnerReference(entandoCustomResource))
                .endMetadata()
                .withNewSpec()
                .withTls(maybeBuildTls(ingressClient))
                .addNewRule()
                .withHost(determineIngressHost(ingressClient))
                .withNewHttp()
                .withPaths(paths)
                .endHttp()
                .endRule().endSpec().build();
    }

    private Map<String, String> forNginxIngress() {
        Map<String, String> result = new HashMap<>();
        EntandoOperatorConfig.getIngressClass().ifPresent(s -> result.put("kubernetes.io/ingress.class", s));
        if (TlsHelper.canAutoCreateTlsSecret() || (entandoCustomResource instanceof HasIngress && ((HasIngress) entandoCustomResource)
                .getTlsSecretName().isPresent())) {

            //for cases where we have https available but the Keycloak redirect was specified as http
            result.put("nginx.ingress.kubernetes.io/force-ssl-redirect", "true");
        }
        if (entandoCustomResource.getSpec() instanceof EntandoIngressingDeploymentSpec) {
            EntandoIngressingDeploymentSpec spec = (EntandoIngressingDeploymentSpec) entandoCustomResource.getSpec();
            spec.getResourceRequirements().ifPresent(resourceRequirements -> resourceRequirements.getFileUploadLimit()
                    .ifPresent(s -> result.put("nginx.ingress.kubernetes.io/proxy-body-size", s)));
        }
        return result;
    }

    private List<IngressTLS> maybeBuildTls(IngressClient ingressClient) {
        List<IngressTLS> result = new ArrayList<>();
        ((HasIngress) entandoCustomResource).getTlsSecretName().ifPresent(s ->
                result.add(new IngressTLSBuilder().withSecretName(s).withHosts(determineIngressHost(ingressClient)).build()));
        if (result.isEmpty() && TlsHelper.canAutoCreateTlsSecret()) {
            result.add(new IngressTLSBuilder().withHosts(determineIngressHost(ingressClient))
                    .withSecretName(entandoCustomResource.getMetadata().getName() + "-tls-secret").build());

        }
        return result;
    }

    private String determineIngressHost(IngressClient ingressClient) {
        //TODO Should we not encapsulate the HasIngress behind the IngressDeployable?
        return ((HasIngress) entandoCustomResource).getIngressHostName()
                .orElse(entandoCustomResource.getMetadata().getName() + "-" + entandoCustomResource.getMetadata().getNamespace() + "."
                        + determineRoutingSuffix(
                        ingressClient.getMasterUrlHost()));
    }

}
