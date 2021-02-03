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

package org.entando.kubernetes.controller.support.creators;

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
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.deployable.Ingressing;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.EntandoCustomResource;

public class IngressCreator extends AbstractK8SResourceCreator {

    private final IngressPathCreator ingressPathCreator;
    private Ingress ingress;

    public IngressCreator(EntandoCustomResource entandoCustomResource) {
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

    public void createIngress(IngressClient ingressClient, IngressingDeployable<?> ingressingDeployable,
            Service service) {
        this.ingress = ingressClient.loadIngress(ingressingDeployable.getIngressNamespace(), ingressingDeployable.getIngressName());
        if (this.ingress == null) {
            Ingress newIngress = newIngress(ingressClient, ingressPathCreator.buildPaths(ingressingDeployable, service),
                    ingressingDeployable);
            this.ingress = ingressClient.createIngress(entandoCustomResource, newIngress);
        } else {
            if (KubeUtils.customResourceOwns(entandoCustomResource, ingress)) {
                this.ingress = ingressClient.editIngress(entandoCustomResource, ingressingDeployable.getIngressName())
                        .editSpec().editFirstRule().withHost(determineIngressHost(ingressClient, ingressingDeployable)).endRule()
                        .withTls(maybeBuildTls(ingressClient, ingressingDeployable)).endSpec().done();
            }
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

    private Ingress newIngress(IngressClient ingressClient, List<HTTPIngressPath> paths,
            IngressingDeployable<?> deployable) {
        return new IngressBuilder()
                .withNewMetadata()
                .withAnnotations(forNginxIngress(deployable))
                .withName(entandoCustomResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX)
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .addToLabels(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName())
                .withOwnerReferences(ResourceUtils.buildOwnerReference(entandoCustomResource))
                .endMetadata()
                .withNewSpec()
                .withTls(maybeBuildTls(ingressClient, deployable))
                .addNewRule()
                .withHost(determineIngressHost(ingressClient, deployable))
                .withNewHttp()
                .withPaths(paths)
                .endHttp()
                .endRule().endSpec().build();
    }

    private Map<String, String> forNginxIngress(IngressingDeployable<?> deployable) {
        Map<String, String> result = new HashMap<>();
        EntandoOperatorConfig.getIngressClass().ifPresent(s -> result.put("kubernetes.io/ingress.class", s));
        if (TlsHelper.canAutoCreateTlsSecret() || deployable.getTlsSecretName().isPresent()) {

            //for cases where we have https available but the Keycloak redirect was specified as http
            result.put("nginx.ingress.kubernetes.io/force-ssl-redirect", "true");
        }
        deployable.getFileUploadLimit().ifPresent(s -> result.put("nginx.ingress.kubernetes.io/proxy-body-size", s));
        return result;
    }

    private List<IngressTLS> maybeBuildTls(IngressClient ingressClient, IngressingDeployable<?> deployable) {
        List<IngressTLS> result = new ArrayList<>();

        deployable.getTlsSecretName().ifPresent(s ->
                result.add(new IngressTLSBuilder().withSecretName(s).withHosts(determineIngressHost(ingressClient, deployable)).build()));
        if (result.isEmpty() && TlsHelper.canAutoCreateTlsSecret()) {
            result.add(new IngressTLSBuilder().withHosts(determineIngressHost(ingressClient, deployable))
                    .withSecretName(entandoCustomResource.getMetadata().getName() + "-tls-secret").build());

        }
        return result;
    }

    private String determineIngressHost(IngressClient ingressClient, IngressingDeployable<?> deployable) {
        return deployable.getIngressHostName()
                .orElse(entandoCustomResource.getMetadata().getName() + "-" + entandoCustomResource.getMetadata().getNamespace() + "."
                        + determineRoutingSuffix(
                        ingressClient.getMasterUrlHost()));
    }

}
