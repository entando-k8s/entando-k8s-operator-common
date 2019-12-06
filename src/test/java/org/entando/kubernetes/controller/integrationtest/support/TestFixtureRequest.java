package org.entando.kubernetes.controller.integrationtest.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;

public class TestFixtureRequest {

    private Map<String, List<Class<? extends EntandoBaseCustomResource>>> requiredDeletions = new ConcurrentHashMap<>();

    private Map<String, List<EntandoBaseCustomResource>> requiredAdditions = new ConcurrentHashMap<>();
    public DeletionRequestBuilder deleteAll(Class<? extends EntandoBaseCustomResource>... types) {
        return new DeletionRequestBuilder(Arrays.asList(types), this);
    }

    public AdditionRequestBuilder addAll(EntandoBaseCustomResource... objects) {
        return new AdditionRequestBuilder(Arrays.asList(objects), this);
    }

    public Map<String, List<Class<? extends EntandoBaseCustomResource>>> getRequiredDeletions() {
        return requiredDeletions;
    }

    public Map<String, List<EntandoBaseCustomResource>> getRequiredAdditions() {
        return requiredAdditions;
    }

    public class DeletionRequestBuilder {

        private List<Class<? extends EntandoBaseCustomResource>> typesToDelete;
        private TestFixtureRequest request;

        private DeletionRequestBuilder(List<Class<? extends EntandoBaseCustomResource>> typesToDelete, TestFixtureRequest request) {
            this.typesToDelete = new ArrayList<>(typesToDelete);
            this.request = request;
        }

        public TestFixtureRequest fromNamespace(String namespace) {
            if (request.requiredDeletions.containsKey(namespace)) {
                request.requiredDeletions.get(namespace).addAll(typesToDelete);
            } else {
                request.requiredDeletions.put(namespace, typesToDelete);
            }
            return request;
        }
    }

    public class AdditionRequestBuilder {

        private List<EntandoBaseCustomResource> objectsToAdd;
        private TestFixtureRequest request;

        private AdditionRequestBuilder(List<EntandoBaseCustomResource> objectsToAdd, TestFixtureRequest request) {
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