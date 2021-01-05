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

package org.entando.kubernetes.controller.spi;

import static java.lang.String.format;

import java.util.Optional;
import org.entando.kubernetes.controller.common.DockerImageInfo;

public class DefaultDockerImageInfo implements DockerImageInfo {

    private final String registryHost;
    private final String organization;
    private final String repository;
    private final String tag;
    private final Integer registryPort;

    public DefaultDockerImageInfo(String imageUri) {
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

    @Override
    public Optional<String> getRegistryHost() {
        return Optional.ofNullable(registryHost);
    }

    @Override
    public Optional<String> getOrganization() {
        return Optional.ofNullable(organization);
    }

    @Override
    public String getRepository() {
        return repository;
    }

    @Override
    public Optional<String> getVersion() {
        return Optional.ofNullable(tag);
    }

    @Override
    public Optional<Integer> getRegistryPort() {
        return Optional.ofNullable(registryPort);
    }
}
