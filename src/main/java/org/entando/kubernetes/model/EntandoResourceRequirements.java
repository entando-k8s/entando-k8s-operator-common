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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Serializable;
import java.util.Optional;

@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE)
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntandoResourceRequirements implements Serializable {

    private String storageRequest;
    private String storageLimit;
    private String memoryRequest;
    private String memoryLimit;
    private String cpuRequest;
    private String cpuLimit;
    private String fileUploadLimit;

    public EntandoResourceRequirements() {
    }

    public EntandoResourceRequirements(@JsonProperty("storageRequest") String storageRequest,
            @JsonProperty("storageLimit") String storageLimit,
            @JsonProperty("memoryRequest") String memoryRequest,
            @JsonProperty("memoryLimit") String memoryLimit,
            @JsonProperty("cpuRequest") String cpuRequest,
            @JsonProperty("cpuLimit") String cpuLimit,
            @JsonProperty("fileUploadLimit") String fileUploadLimit) {
        this.storageRequest = storageRequest;
        this.storageLimit = storageLimit;
        this.memoryRequest = memoryRequest;
        this.memoryLimit = memoryLimit;
        this.cpuRequest = cpuRequest;
        this.cpuLimit = cpuLimit;
        this.fileUploadLimit = fileUploadLimit;
    }

    public Optional<String> getStorageRequest() {
        return Optional.ofNullable(storageRequest);
    }

    public Optional<String> getStorageLimit() {
        return Optional.ofNullable(storageLimit);
    }

    public Optional<String> getMemoryRequest() {
        return Optional.ofNullable(memoryRequest);
    }

    public Optional<String> getMemoryLimit() {
        return Optional.ofNullable(memoryLimit);
    }

    public Optional<String> getCpuRequest() {
        return Optional.ofNullable(cpuRequest);
    }

    public Optional<String> getCpuLimit() {
        return Optional.ofNullable(cpuLimit);
    }

    public Optional<String> getFileUploadLimit() {
        return Optional.ofNullable(fileUploadLimit);
    }
}
