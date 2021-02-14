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

package org.entando.kubernetes.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Optional;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
@RegisterForReflection
@JsonIgnoreProperties(
        ignoreUnknown = true
)

public class DeserializedEntandoResource implements EntandoCustomResource {

    @JsonIgnore
    private CustomResourceDefinition definition;
    private EntandoCustomResourceStatus status;
    private ObjectMeta metadata;
    private String kind;
    private String apiVersion;
    public void setDefinition(CustomResourceDefinition definition) {
        this.definition = definition;
    }

    public CustomResourceDefinition getDefinition() {
        return definition;
    }

    @Override
    public EntandoCustomResourceStatus getStatus() {
        if (this.status == null) {
            this.status = new EntandoCustomResourceStatus();
        }
        return this.status;
    }

    @Override
    public void setStatus(EntandoCustomResourceStatus status) {
        this.status=status;
    }

    @Override
    public String getDefinitionName() {
        return null;
    }

    @Override
    public ObjectMeta getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getApiVersion() {
        return Optional.ofNullable(definition).map(crd->crd.getSpec().getGroup() + "/" + crd.getSpec().getVersion()).orElse(apiVersion);
    }

    @Override
    public String getKind() {
        return Optional.ofNullable(definition).map(crd->crd.getSpec().getNames().getKind()).orElse(kind);
    }

    @Override
    public void setApiVersion(String version) {
    }
}
