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

package org.entando.kubernetes.model.keycloakserver;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.EntandoResourceRequirements;

@JsonInclude(Include.NON_NULL)
@JsonSerialize
@JsonDeserialize()
@JsonAutoDetect(fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE)
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntandoKeycloakServerSpec extends EntandoIngressingDeploymentSpec {

    private String customImage;
    private StandardKeycloakImage standardImage;
    private boolean isDefault;
    private String frontEndUrl;

    public EntandoKeycloakServerSpec() {
        super();
    }

    @JsonCreator
    public EntandoKeycloakServerSpec(
            @JsonProperty("customName") String customImage,
            @JsonProperty("standardImage") StandardKeycloakImage standardImage,
            @JsonProperty("frontEndUrl") String frontEndUrl,
            @JsonProperty("dbms") DbmsVendor dbms,
            @JsonProperty("ingressHostName") String ingressHostName,
            @JsonProperty("tlsSecretName") String tlsSecretName,
            @JsonProperty("replicas") Integer replicas,
            @JsonProperty("isDefault") Boolean isDefault,
            @JsonProperty("serviceAccountToUse") String serviceAccountToUse,
            @JsonProperty("environmentVariables") List<EnvVar> environmentVariables,
            @JsonProperty("resourceRequirements") EntandoResourceRequirements resourceRequirements) {
        super(ingressHostName, tlsSecretName, replicas, dbms, serviceAccountToUse, environmentVariables, resourceRequirements);
        this.customImage = customImage;
        this.standardImage = standardImage;
        this.frontEndUrl = frontEndUrl;
        this.isDefault = Boolean.TRUE.equals(isDefault);
    }

    public Optional<String> getCustomImage() {
        return Optional.ofNullable(customImage);
    }

    public Optional<String> getFrontEndUrl() {
        return Optional.ofNullable(frontEndUrl);
    }

    public Optional<StandardKeycloakImage> getStandardImage() {
        return Optional.ofNullable(standardImage);
    }

    public boolean isDefault() {
        return isDefault;
    }
}
