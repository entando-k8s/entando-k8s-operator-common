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

package org.entando.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@JsonSerialize
@JsonDeserialize
@JsonTypeInfo(
        use = Id.NAME,
        include = As.EXISTING_PROPERTY,
        property = "type"

)
@JsonSubTypes({
        @Type(value = WebServerStatus.class, name = "WebServerStatus"),
        @Type(value = DbServerStatus.class, name = "DbServerStatus"),
})
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE)
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractServerStatus implements Serializable {

    private String qualifier;
    private String type = getClass().getSimpleName();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ", timezone = "GMT")
    private Date started = new Date();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ", timezone = "GMT")
    private Date finished;
    private ServiceStatus serviceStatus;
    private DeploymentStatus deploymentStatus;
    private PodStatus podStatus;
    private EntandoControllerFailure entandoControllerFailure;
    private List<PersistentVolumeClaimStatus> persistentVolumeClaimStatuses;
    private PodStatus initPodStatus;

    protected AbstractServerStatus() {
        //For json deserialization
    }

    protected AbstractServerStatus(String qualifier) {
        this.qualifier = qualifier;
    }

    public void finish() {
        this.finished = new Date();
    }

    public Date getStarted() {
        return started;
    }

    public Date getFinished() {
        return finished;
    }

    public ServiceStatus getServiceStatus() {
        return serviceStatus;
    }

    public void setServiceStatus(ServiceStatus serviceStatus) {
        this.serviceStatus = serviceStatus;
    }

    public DeploymentStatus getDeploymentStatus() {
        return deploymentStatus;
    }

    public void setDeploymentStatus(DeploymentStatus deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
    }

    public PodStatus getPodStatus() {
        return podStatus;
    }

    public void setPodStatus(PodStatus podStatus) {
        this.podStatus = podStatus;
    }

    public EntandoControllerFailure getEntandoControllerFailure() {
        return entandoControllerFailure;
    }

    public void setEntandoControllerFailure(EntandoControllerFailure entandoControllerFailure) {
        this.entandoControllerFailure = entandoControllerFailure;
    }

    public boolean hasFailed() {
        return entandoControllerFailure != null;
        //TODO incorporate pod status here
        //Requires PodResult class from entando-k8s-operator
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public List<PersistentVolumeClaimStatus> getPersistentVolumeClaimStatuses() {
        return persistentVolumeClaimStatuses;
    }

    public void setPersistentVolumeClaimStatuses(List<PersistentVolumeClaimStatus> persistentVolumeClaimStatuses) {
        this.persistentVolumeClaimStatuses = persistentVolumeClaimStatuses;
    }

    public PodStatus getInitPodStatus() {
        return initPodStatus;
    }

    public void setInitPodStatus(PodStatus initPodStatus) {
        this.initPodStatus = initPodStatus;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void finishWith(EntandoControllerFailure failure) {
        finish();
        setEntandoControllerFailure(failure);
    }
}
