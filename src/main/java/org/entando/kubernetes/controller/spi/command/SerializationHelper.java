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

package org.entando.kubernetes.controller.spi.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext.Builder;
import java.beans.Introspector;
import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.entando.kubernetes.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DatabasePopulator;
import org.entando.kubernetes.controller.spi.container.DbAware;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.HasWebContext;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.IngressingPathOnPort;
import org.entando.kubernetes.controller.spi.container.KeycloakAwareContainer;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.container.TrustStoreAware;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Ingressing;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ServiceResult;

public class SerializationHelper<T extends ServiceDeploymentResult<T>> {

    protected final KubernetesClient kubernetesClient;
    List<Class<?>> knownInterfaces = Arrays.asList(
            ConfigurableResourceContainer.class,
            DatabasePopulator.class,
            DbAware.class,
            DbAwareDeployable.class,
            Deployable.class,
            DeployableContainer.class,
            HasHealthCommand.class,
            HasWebContext.class,
            Ingressing.class,
            IngressingContainer.class,
            IngressingDeployable.class,
            IngressingPathOnPort.class,
            KeycloakAwareContainer.class,
            ParameterizableContainer.class,
            PersistentVolumeAware.class,
            PublicIngressingDeployable.class,
            Secretive.class,
            ServiceBackingContainer.class,
            ServiceDeploymentResult.class,
            ServiceResult.class,
            TrustStoreAware.class,
            SerializableDeploymentResult.class
    );

    public SerializationHelper(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unchecked")
    public <S> S deserialize(String json) {
        return rethrowsAsRuntimes(() -> {
            Map<String, Object> map = new ObjectMapper().readValue(new StringReader(json), Map.class);
            return (S) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    getImplementedInterfaces(map),
                    getInvocationHandler(map)
            );
        });
    }

    public String serialize(Object deployable) {
        Map<String, Object> map = toJsonFriendlyMap(deployable);
        return rethrowsAsRuntimes(() -> new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(map));
    }

    private static String propertyName(Method m) {
        String name = m.getName();
        if (name.startsWith("get")) {
            return Introspector.decapitalize(name.substring(3));
        } else if (name.startsWith("is") && m.getReturnType() == Boolean.TYPE) {
            return Introspector.decapitalize(name.substring(2));
        }
        return null;
    }

    private <A extends Annotation> A getAnnotation(Class<?> c, String methodName, Class<A> annotationType) {
        final Method method;
        try {
            method = c.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
        A annotation = method.getAnnotation(annotationType);
        if (annotation == null && c.getSuperclass() != Object.class && !c.isInterface()) {
            annotation = getAnnotation(c.getSuperclass(), methodName, annotationType);
        }
        if (annotation == null) {
            annotation = getAnnotationFromInterfaces(c.getInterfaces(), methodName, annotationType);
        }
        return annotation;
    }

    protected <A extends Annotation> A getAnnotationFromInterfaces(Class<?>[] interfaces, String methodName, Class<A> annotationType) {
        for (Class<?> intf : interfaces) {
            A annotation = getAnnotation(intf, methodName, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private Map<String, Object> toJsonFriendlyMap(Object nonSerializableObject) {
        Map<String, Object> map = new HashMap<>();
        map.put("mixins", Arrays.stream(getAllImplementedInterfaces(nonSerializableObject.getClass()))
                .filter(knownInterfaces::contains)
                .map(Class::getSimpleName)
                .collect(Collectors.toList()));
        map.putAll(Arrays.stream(nonSerializableObject.getClass().getMethods())
                .filter(method -> (method.getName().startsWith("get") || method.getName().startsWith("is"))
                        && method.getReturnType() != void.class
                        && method.getParameterCount() == 0)
                .map(method -> processGetter(nonSerializableObject, method))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
        return map;
    }

    private Class<?>[] getAllImplementedInterfaces(Class<?> clazz) {
        Set<Class<?>> result = new HashSet<>();
        while (clazz != Object.class) {
            result.addAll(Arrays.asList(clazz.getInterfaces()));
            clazz = clazz.getSuperclass();
        }
        return result.toArray(Class[]::new);
    }

    private Pair<String, Object> processGetter(Object nonSerializableObject, Method method) {
        return rethrowsAsRuntimes(() -> {
            Object value = method.invoke(nonSerializableObject);
            if (value != null) {
                if (getAnnotation(nonSerializableObject.getClass(), method.getName(), SerializeByReference.class) != null) {
                    return new ImmutablePair<>(propertyName(method), new ResourceReference((HasMetadata) value));
                } else if (getAnnotation(nonSerializableObject.getClass(), method.getName(), JsonIgnore.class) == null) {
                    return Optional.ofNullable(toJsonSafeValue(value))
                            .map(jsonSafeValue -> new ImmutablePair<>(propertyName(method), jsonSafeValue))
                            .orElse(null);
                }
            }
            return null;
        });
    }

    private Object toJsonSafeValue(Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        } else if (value instanceof Optional) {
            return ((Optional<?>) value).map(this::toJsonSafeValue).orElse(null);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().map(this::toJsonSafeValue).collect(Collectors.toList());
        } else if (value != null) {
            if (value.getClass().getAnnotation(JsonDeserialize.class) != null) {
                //We know how to serialize this
                return value;
            } else if (value.getClass().getName().startsWith("org.entando.kubernetes.controller")) {
                //Can't serialize, but is known, so translate to map
                return this.toJsonFriendlyMap(value);
            }
        }
        return null;
    }

    @SuppressWarnings("java:S112")
    //Because this is generic exception handling code
    protected <S> S rethrowsAsRuntimes(FailableSupplier<S> supplier) {
        try {
            return supplier.supply();
        } catch (RuntimeException r) {
            throw r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object coerce(Object object, Class<?> type) {
        if (object == null) {
            return null;
        } else if (Number.class.isAssignableFrom(type)) {
            try {
                return type.getConstructor(String.class).newInstance(object.toString());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalStateException("Could not coerce to number: " + object, e);
            }
        } else {
            throw new IllegalArgumentException(type + " not supported");

        }
    }

    @SuppressWarnings("unchecked")
    private InvocationHandler getInvocationHandler(Map<String, Object> map) {
        return (o, method, objects) -> {
            if (method.getName().equals("createResult")) {
                return createResult(objects);
            }
            Object result = map.get(propertyName(method));
            if (result == null) {
                if (Optional.class.isAssignableFrom(method.getReturnType())) {
                    return Optional.empty();
                }
            } else {
                if (getAnnotationFromInterfaces(getImplementedInterfaces(map), method.getName(), SerializeByReference.class) != null) {
                    return resolveByReference(result);
                } else if (Optional.class.isAssignableFrom(method.getReturnType())) {
                    return Optional.ofNullable(coerce(result, resolveFirstTypeArgument(method)));
                } else if (method.getReturnType().getAnnotation(JsonDeserialize.class) != null) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(result)),
                            method.getReturnType());
                } else if (method.getReturnType() == List.class) {
                    return serializeList(method, (List<Map<String, Object>>) result);

                }
            }
            return result;

        };
    }

    private List<?> serializeList(Method method, List<Map<String, Object>> result) {
        Class<?> typeArgument = (Class<?>) ((ParameterizedType) method.getGenericReturnType())
                .getActualTypeArguments()[0];
        if (typeArgument.getAnnotation(JsonDeserialize.class) != null) {
            return deserializeListOfMaps(result, typeArgument);
        } else if (method.getName().equals("getContainers")) {
            return deserializeContainers(result);
        } else {
            return Collections.emptyList();
        }
    }

    private HasMetadata resolveByReference(Object result) throws IOException {
        //TODO support lists
        ObjectMapper objectMapper = new ObjectMapper();
        final ResourceReference resourceReference = objectMapper
                .readValue(new StringReader(objectMapper.writeValueAsString(result)),
                        ResourceReference.class);
        if (resourceReference.isCustomResource()) {
            return serializeCustomResource(objectMapper, resourceReference);
        } else {
            return resolveStandardResource(resourceReference);
        }
    }

    private List<Object> deserializeContainers(List<Map<String, Object>> result) {
        return result.stream().map(deserializedMap ->
                Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                        getImplementedInterfaces(deserializedMap), getInvocationHandler(deserializedMap)))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("java:S112")
    //Because this is generic exception handling code
    private List<?> deserializeListOfMaps(List<Map<String, Object>> result, Class<?> typeArgument) {
        return result.stream().map(deserializedMap -> {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(deserializedMap)),
                        typeArgument);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private HasMetadata resolveStandardResource(ResourceReference resourceReference) {
        return SupportedResourceKind.resolveFromKind(resourceReference.getKind())
                .map(k -> k.getOperation(kubernetesClient)
                        .inNamespace(resourceReference.getMetadata().getNamespace())
                        .withName(resourceReference.getMetadata().getName())
                        .fromServer()
                        .get())
                .orElseThrow(() -> new IllegalStateException(
                        "Resource kind '" + resourceReference.getKind() + "' not supported."));
    }

    private SerializedEntandoResource serializeCustomResource(ObjectMapper objectMapper, ResourceReference resourceReference)
            throws IOException {
        final CustomResourceDefinition definition = kubernetesClient.apiextensions().v1beta1().customResourceDefinitions()
                .list().getItems()
                .stream().filter(crd ->
                        crd.getSpec().getNames().getKind().equals(resourceReference.getKind()) && resourceReference
                                .getApiVersion()
                                .startsWith(crd.getSpec().getGroup())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find CRD for " + resourceReference.getKind()));
        final Map<String, Object> crMap = kubernetesClient.customResource(new Builder()
                .withName(definition.getMetadata().getName())
                .withGroup(definition.getSpec().getGroup())
                .withScope(definition.getSpec().getScope())
                .withVersion(definition.getSpec().getVersion())
                .withPlural(definition.getSpec().getNames().getPlural())
                .build())
                .get(resourceReference.getMetadata().getNamespace(), resourceReference.getMetadata().getName());

        final SerializedEntandoResource serializedEntandoResource = objectMapper
                .readValue(new StringReader(objectMapper.writeValueAsString(crMap)),
                        SerializedEntandoResource.class);
        serializedEntandoResource.setDefinition(CustomResourceDefinitionContext.fromCrd(definition));
        return serializedEntandoResource;
    }

    private Object createResult(Object[] objects)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> selectedConstructor = DefaultSerializableDeploymentResult.class.getConstructors()[0];
        final Object[] arguments = Arrays.stream(selectedConstructor.getParameterTypes())
                .map(type -> Arrays.stream(objects).filter(type::isInstance).findFirst().orElse(null))
                .toArray(Object[]::new);
        return selectedConstructor.newInstance(arguments);
    }

    private Class<?> resolveFirstTypeArgument(Method method) {
        return (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
    }

    @SuppressWarnings("unchecked")
    Class<?>[] getImplementedInterfaces(Map<String, Object> map) {
        List<String> mixins = (List<String>) map.get("mixins");
        return knownInterfaces.stream().filter(aClass -> mixins.contains(aClass.getSimpleName())).toArray(Class<?>[]::new);
    }

}
