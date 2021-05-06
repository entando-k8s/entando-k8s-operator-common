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

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.CustomResourceClient;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;

public class DeserializationHelper implements InvocationHandler {

    private final Map<String, Object> map;
    private final CustomResourceClient kubernetesClient;
    private final ObjectMapper objectMapper;

    private DeserializationHelper(CustomResourceClient kubernetesClient, Map<String, Object> map, ObjectMapper objectMapper) {
        this.kubernetesClient = kubernetesClient;
        this.map = map;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public static <S> S deserialize(CustomResourceClient kubernetesClient, String json) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return fromMap(kubernetesClient, map, objectMapper);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <S> S fromMap(CustomResourceClient kubernetesClient, Map<String, Object> map,
            ObjectMapper objectMapper) {
        return (S) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                getImplementedInterfaces(map),
                new DeserializationHelper(kubernetesClient, map, objectMapper)
        );
    }

    @SuppressWarnings("unchecked")
    public static Class<?>[] getImplementedInterfaces(Map<String, Object> map) {
        List<String> mixins = (List<String>) map.get("mixins");
        return ReflectionUtil.KNOWN_INTERFACES.stream().filter(aClass -> mixins.contains(aClass.getSimpleName())).toArray(Class<?>[]::new);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        if (method.getName().equals("createResult")) {
            return createResult(objects);
        }
        Object result = map.get(ReflectionUtil.propertyName(method));
        if (result == null) {
            if (Optional.class.isAssignableFrom(method.getReturnType())) {
                return Optional.empty();
            }
        } else {
            if (ReflectionUtil
                    .getAnnotationFromInterfaces(getImplementedInterfaces(map), method.getName(),
                            SerializeByReference.class) != null) {
                return resolveByReference(result);
            } else if (Optional.class.isAssignableFrom(method.getReturnType())) {
                return Optional.ofNullable(coerceSimpletype(result, resolveFirstTypeArgument(method)));
            } else if (method.getReturnType().getAnnotation(JsonDeserialize.class) != null) {
                return objectMapper.readValue(objectMapper.writeValueAsString(result), method.getReturnType());
            } else if (method.getReturnType() == List.class) {
                return deserializeList(method, (List<Map<String, Object>>) result);

            }
        }
        return result;

    }

    private Class<?> resolveFirstTypeArgument(Method method) {
        return (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
    }

    private List<?> deserializeList(Method method, List<Map<String, Object>> result) {
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

    private List<Object> deserializeContainers(List<Map<String, Object>> result) {
        return result.stream().map(deserializedMap -> fromMap(kubernetesClient, deserializedMap, objectMapper))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("java:S112")
    //Because this is generic exception handling code
    private List<?> deserializeListOfMaps(List<Map<String, Object>> result, Class<?> typeArgument) {
        return result.stream().map(deserializedMap -> {
            try {
                return objectMapper.readValue(objectMapper.writeValueAsString(deserializedMap), typeArgument);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private Object createResult(Object[] objects)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> selectedConstructor = DefaultSerializableDeploymentResult.class.getConstructors()[0];
        final Object[] arguments = Arrays.stream(selectedConstructor.getParameterTypes())
                .map(type -> Arrays.stream(objects).filter(type::isInstance).findFirst().orElse(null))
                .toArray(Object[]::new);
        return selectedConstructor.newInstance(arguments);
    }

    private HasMetadata resolveByReference(Object result) throws IOException {
        //TODO support lists
        final ResourceReference resourceReference = objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(result)),
                ResourceReference.class);
        if (resourceReference.isCustomResource()) {
            return kubernetesClient.loadCustomResource(resourceReference.getApiVersion(),
                    resourceReference.getKind(), resourceReference.getMetadata().getNamespace(), resourceReference.getMetadata().getName());
        } else {
            return kubernetesClient.loadStandardResource(resourceReference.getKind(), resourceReference.getMetadata().getNamespace(),
                    resourceReference.getMetadata().getName());
        }
    }

    private Object coerceSimpletype(Object object, Class<?> type) {
        if (object == null) {
            return null;
        }
        try {
            return type.getConstructor(String.class).newInstance(object.toString());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(
                    format("Could not coerce %s to %s: no single String constructor found.", object, type.getName()), e);
        }
    }

}



