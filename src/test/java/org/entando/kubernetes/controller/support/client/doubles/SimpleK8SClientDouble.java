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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.client.VersionInfo;
import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.client.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.mockito.Mockito;

public class SimpleK8SClientDouble extends AbstractK8SClientDouble implements SimpleK8SClient<EntandoResourceClientDouble> {

    private final ServiceClient serviceClient = Mockito.spy(new ServiceClientDouble(getNamespaces()));
    private final PersistentVolumeClaimClient persistentVolumeClaimClient = Mockito
            .spy(new PersistentVolumentClaimClientDouble(
                    getNamespaces()));
    private final IngressClient ingressClient = Mockito.spy(new IngressClientDouble(getNamespaces()));
    private final DeploymentClient deploymentClient;
    private final SecretClient secretClient = Mockito.spy(new SecretClientDouble(getNamespaces()));
    private final EntandoResourceClientDouble entandoResourceClient = Mockito.spy(new EntandoResourceClientDouble(getNamespaces()));
    private final PodClient podClient = Mockito.spy(new PodClientDouble(getNamespaces()));
    private final ServiceAccountClientDouble serviceAccountClient = Mockito.spy(new ServiceAccountClientDouble(getNamespaces()));
    private final VersionInfo version;

    public SimpleK8SClientDouble() {
        this(getVersionInfo(20));
    }

    public SimpleK8SClientDouble(int minorVersion) {
        this(getVersionInfo(minorVersion));
    }

    public SimpleK8SClientDouble(VersionInfo version) {
        this.version = version;
        this.deploymentClient = Mockito.spy(new DeploymentClientDouble(getNamespaces(), version));
    }

    private static VersionInfo getVersionInfo(int minorVersion) {
        final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
        try {
            data.put("minor", String.valueOf(minorVersion));
            data.put("major", "1");
            data.put("gitCommit", "123");
            data.put("gitVersion", "1");
            data.put("buildDate", "2021-01-31T14:00:12Z");
            data.put("MAJOR", "1");
            data.put("MAJOR", "1");
            return new VersionInfo(data);
        } catch (ParseException e) {
            //no need to do anything here
            return null;
        }
    }

    public VersionInfo getVersion() {
        return version;
    }

    @Override
    public ServiceClient services() {
        return this.serviceClient;
    }

    @Override
    public PodClient pods() {
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        return this.secretClient;
    }

    @Override
    public EntandoResourceClientDouble entandoResources() {
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        return this.persistentVolumeClaimClient;
    }

}
