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
import java.util.List;
import java.util.Optional;

@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE)
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class EntandoIngressingDeploymentSpec extends EntandoDeploymentSpec implements HasIngress {

    private String ingressHostName;
    private String tlsSecretName;
    protected DbmsVendor dbms;

    protected EntandoIngressingDeploymentSpec() {
    }

    //Acceptable because it is only used from JsonCreator constructors
    @SuppressWarnings("java:S107")
    protected EntandoIngressingDeploymentSpec(String ingressHostName,
            String tlsSecretName,
            Integer replicas,
            DbmsVendor dbms,
            String serviceAccountToUse,
            List<EnvVar> environmentVariables,
            EntandoResourceRequirements resourceRequirements) {
        super(replicas, serviceAccountToUse, environmentVariables, resourceRequirements);
        this.ingressHostName = ingressHostName;
        this.tlsSecretName = tlsSecretName;
        this.dbms = dbms;
    }

    public Optional<DbmsVendor> getDbms() {
        return ofNullable(dbms);
    }

    @Override
    public Optional<String> getIngressHostName() {
        return Optional.ofNullable(ingressHostName);
    }

    @Override
    public Optional<String> getTlsSecretName() {
        return Optional.ofNullable(tlsSecretName);
    }

}
