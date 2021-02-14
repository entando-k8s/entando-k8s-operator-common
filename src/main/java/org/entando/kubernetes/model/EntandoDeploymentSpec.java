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

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE)
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)

public abstract class EntandoDeploymentSpec implements Serializable {

    private Integer replicas = 1;
    private String serviceAccountToUse;
    private List<EnvVar> environmentVariables;
    private EntandoResourceRequirements resourceRequirements;

    protected EntandoDeploymentSpec() {
    }

    protected EntandoDeploymentSpec(Integer replicas,
            String serviceAccountToUse,
            List<EnvVar> environmentVariables,
            EntandoResourceRequirements resourceRequirements) {
        this.replicas = replicas;
        this.serviceAccountToUse = serviceAccountToUse;
        this.environmentVariables = environmentVariables;
        this.resourceRequirements = resourceRequirements;
    }

    public Optional<Integer> getReplicas() {
        return ofNullable(replicas);
    }

    public List<EnvVar> getEnvironmentVariables() {
        return environmentVariables == null ? Collections.emptyList() : environmentVariables;
    }

    public Optional<String> getServiceAccountToUse() {
        return ofNullable(serviceAccountToUse);
    }

    public Optional<EntandoResourceRequirements> getResourceRequirements() {
        return Optional.ofNullable(resourceRequirements);
    }
}
