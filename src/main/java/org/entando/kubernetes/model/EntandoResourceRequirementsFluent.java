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

public abstract class EntandoResourceRequirementsFluent<N extends EntandoResourceRequirementsFluent> {

    private String storageRequest;
    private String storageLimit;
    private String memoryRequest;
    private String memoryLimit;
    private String cpuRequest;
    private String cpuLimit;
    private String fileUploadLimit;

    protected EntandoResourceRequirementsFluent(EntandoResourceRequirements resourceRequirements) {
        this.storageRequest = resourceRequirements.getStorageRequest().orElse(null);
        this.storageLimit = resourceRequirements.getStorageLimit().orElse(null);
        this.memoryRequest = resourceRequirements.getMemoryRequest().orElse(null);
        this.memoryLimit = resourceRequirements.getMemoryLimit().orElse(null);
        this.cpuRequest = resourceRequirements.getCpuRequest().orElse(null);
        this.cpuLimit = resourceRequirements.getCpuLimit().orElse(null);
        this.fileUploadLimit = resourceRequirements.getFileUploadLimit().orElse(null);
    }

    protected EntandoResourceRequirementsFluent() {

    }

    public final N withStorageRequest(String storageRequest) {
        this.storageRequest = storageRequest;
        return thisAsN();
    }

    public final N withStorageLimit(String storageLimit) {
        this.storageLimit = storageLimit;
        return thisAsN();
    }

    public final N withMemoryRequest(String memoryRequest) {
        this.memoryRequest = memoryRequest;
        return thisAsN();
    }

    public final N withMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
        return thisAsN();
    }

    public final N withCpuRequest(String cpuRequest) {
        this.cpuRequest = cpuRequest;
        return thisAsN();
    }

    public final N withCpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
        return thisAsN();
    }

    public final N withFileUploadLimit(String fileUploadLimit) {
        this.fileUploadLimit = fileUploadLimit;
        return thisAsN();
    }

    public EntandoResourceRequirements build() {
        return new EntandoResourceRequirements(storageRequest, storageLimit, memoryRequest, memoryLimit, cpuRequest, cpuLimit,
                fileUploadLimit);
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }
}
