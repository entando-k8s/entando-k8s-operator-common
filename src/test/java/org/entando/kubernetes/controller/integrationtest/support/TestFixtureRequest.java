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

package org.entando.kubernetes.controller.integrationtest.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.EntandoBaseCustomResource;

public class TestFixtureRequest {

    private Map<String, List<Class<? extends EntandoBaseCustomResource<?>>>> requiredDeletions = new ConcurrentHashMap<>();

    private Map<String, List<EntandoBaseCustomResource<?>>> requiredAdditions = new ConcurrentHashMap<>();

    public DeletionRequestBuilder deleteAll(Class<? extends EntandoBaseCustomResource<?>> type) {
        return new DeletionRequestBuilder(type, this);
    }

    public AdditionRequestBuilder addAll(EntandoBaseCustomResource... objects) {
        return new AdditionRequestBuilder(Arrays.asList(objects), this);
    }

    public Map<String, List<Class<? extends EntandoBaseCustomResource<?>>>> getRequiredDeletions() {
        return requiredDeletions;
    }

    public Map<String, List<EntandoBaseCustomResource<?>>> getRequiredAdditions() {
        return requiredAdditions;
    }

    public class DeletionRequestBuilder {

        private Class<? extends EntandoBaseCustomResource<?>> typesToDelete;
        private TestFixtureRequest request;

        private DeletionRequestBuilder(Class<? extends EntandoBaseCustomResource<?>> typesToDelete, TestFixtureRequest request) {
            this.typesToDelete = typesToDelete;
            this.request = request;
        }

        public TestFixtureRequest fromNamespace(String namespace) {
            List<Class<? extends EntandoBaseCustomResource<?>>> types = request.requiredDeletions
                    .computeIfAbsent(namespace, k -> new ArrayList<>());
            types.add(typesToDelete);
            return request;
        }
    }

    public static class AdditionRequestBuilder {

        private final List<EntandoBaseCustomResource<?>> objectsToAdd;
        private final TestFixtureRequest request;

        private AdditionRequestBuilder(List<EntandoBaseCustomResource<?>> objectsToAdd, TestFixtureRequest request) {
            this.objectsToAdd = new ArrayList<>(objectsToAdd);
            this.request = request;
        }

        public TestFixtureRequest toNamespace(String namespace) {
            if (request.requiredAdditions.containsKey(namespace)) {
                request.requiredAdditions.get(namespace).addAll(objectsToAdd);
            } else {
                request.requiredAdditions.put(namespace, objectsToAdd);
            }
            return request;
        }
    }
}