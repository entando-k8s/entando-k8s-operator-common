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

package org.entando.kubernetes.controller.spi.container;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
public class DockerImageInfo {

    private final String registryHost;
    private final String organization;
    private final String repository;
    private final String tag;
    private final Integer registryPort;

    @JsonCreator
    public DockerImageInfo(
            @JsonProperty("registryHost") String registryHost,
            @JsonProperty("registryPort") Integer registryPort,
            @JsonProperty("organization") String organization,
            @JsonProperty("repository") String repository,
            @JsonProperty("tag") String tag) {
        this.registryHost = registryHost;
        this.organization = organization;
        this.repository = repository;
        this.tag = tag;
        this.registryPort = registryPort;
    }

    public DockerImageInfo(DbmsDockerVendorStrategy dockerVendorStrategy) {
        //'latest' because these are vendor provided images and the versions of both the DB and OS is in the image name
        this(
                dockerVendorStrategy.getRegistry(),
                null,
                dockerVendorStrategy.getOrganization(),
                dockerVendorStrategy.getImageRepository(),
                "latest");
    }

    public DockerImageInfo(String imageUri) {
        String[] segments = imageUri.split("/");
        String[] repositorySegments = segments[segments.length - 1].split(":");
        if (repositorySegments.length == 1) {
            repository = repositorySegments[0];
            tag = null;
        } else if (repositorySegments.length == 2) {
            repository = repositorySegments[0];
            tag = repositorySegments[1];
        } else {
            throw new IllegalArgumentException(
                    format("The repository '%s' is not supported. At most one colon (:) allowed.", segments[segments.length - 1]));
        }
        if (segments.length == 4) {
            organization = segments[segments.length - 3] + "/" + segments[segments.length - 2];
        } else if (segments.length >= 2) {
            organization = segments[segments.length - 2];
        } else {
            organization = null;
        }
        if (segments.length >= 3) {
            String[] hostSegments = segments[0].split(":");
            registryHost = hostSegments[0];
            if (hostSegments.length == 1) {
                registryPort = null;
            } else if (hostSegments.length == 2) {
                registryPort = Integer.valueOf(hostSegments[1]);
            } else {
                throw new IllegalArgumentException(
                        format("The registry '%s' is not supported. At most one colon (:) allowed.", segments[0]));
            }
        } else {
            registryPort = null;
            registryHost = null;
        }
        if (segments.length > 4) {
            throw new IllegalArgumentException(
                    format("The imageUri '%s' is not supported. Paths on the image name may not contain more than 3 segments", imageUri));

        }
    }

    public Optional<String> getRegistryHost() {
        return Optional.ofNullable(registryHost);
    }

    public Optional<String> getOrganization() {
        return Optional.ofNullable(organization);
    }

    public String getRepository() {
        return repository;
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(tag);
    }

    public Optional<Integer> getRegistryPort() {
        return Optional.ofNullable(registryPort);
    }

    public Optional<String> getRegistry() {
        return getRegistryHost().map(s -> s + getRegistryPort().map(integer -> ":" + integer).orElse(""));
    }

    public String getOrganizationAwareRepository() {
        return getOrganization().map(s -> s.replace("/", "-") + "-").orElse("") + getRepository();
    }
}
