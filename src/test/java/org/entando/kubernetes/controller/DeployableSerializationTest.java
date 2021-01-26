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

package org.entando.kubernetes.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.beans.Introspector;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestData;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DatabasePopulator;
import org.entando.kubernetes.controller.spi.container.DbAware;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.HasWebContext;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.IngressingPathOnPort;
import org.entando.kubernetes.controller.spi.container.KeycloakAware;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.container.TlsAware;
import org.entando.kubernetes.controller.spi.database.DatabaseDeployable;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Ingressing;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ServiceResult;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.junit.jupiter.api.Test;

//And experiment in JSON serialization
class DeployableSerializationTest implements InProcessTestData {

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
            KeycloakAware.class,
            ParameterizableContainer.class,
            PersistentVolumeAware.class,
            PublicIngressingDeployable.class,
            Secretive.class,
            ServiceBackingContainer.class,
            ServiceDeploymentResult.class,
            ServiceResult.class,
            TlsAware.class
    );

    @Test
    void testDeserialize() throws Exception {
        String json = toJson(new DatabaseDeployable<>(DbmsDockerVendorStrategy.CENTOS_MYSQL, newTestEntandoApp(), null));
        Map<String, Object> map = new ObjectMapper().readValue(new StringReader(json), Map.class);
        Deployable<?, ?> deployabe = (Deployable<?, ?>) Proxy
                .newProxyInstance(Thread.currentThread().getContextClassLoader(), getImplementedInterfaces(map), getInvocationHandler(map)
                );
        EntandoBaseCustomResource customResource = deployabe.getCustomResource();
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(customResource));
        System.out.println(deployabe.getFileSystemUserAndGroupId());
        System.out.println(deployabe.getNameQualifier());
        System.out.println(deployabe.getServiceAccountName());
        System.out.println(deployabe.getReplicas());
        assertThat(deployabe, is(instanceOf(Secretive.class)));
        System.out.println(((Secretive) deployabe).getSecrets());
        DeployableContainer o = deployabe.getContainers().get(0);
        assertThat(o, is(instanceOf(ServiceBackingContainer.class)));
        assertThat(o, is(instanceOf(PersistentVolumeAware.class)));
        System.out.println(((PersistentVolumeAware) o).getVolumeMountPath());
        System.out.println(((PersistentVolumeAware) o).getStorageLimitMebibytes());
        assertThat(o, is(instanceOf(HasHealthCommand.class)));
        System.out.println(((HasHealthCommand) o).getHealthCheckCommand());
        System.out.println(o.getNameQualifier());
        System.out.println(o.getPrimaryPort());
        System.out.println(o.getSecretsToMount());
        System.out.println(o.getDockerImageInfo().getRegistry());
        System.out.println(o.getDockerImageInfo().getOrganization());
        System.out.println(o.getDockerImageInfo().getRegistryHost());
        System.out.println(o.getDockerImageInfo().getRepository());
        System.out.println(o.getDockerImageInfo().getVersion());

        //        System.out.println(json);
    }

    private InvocationHandler getInvocationHandler(Map<String, Object> map) {
        return (o, method, objects) -> {
            Object result = map.get(propertyName(method));
            if (Optional.class.isAssignableFrom(method.getReturnType())) {
                return Optional.ofNullable(result);
            } else if (method.getReturnType().getAnnotation(JsonDeserialize.class) != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(result)),
                        method.getReturnType());
            } else if (method.getReturnType() == List.class) {
                Class<?> typeArgument = (Class<?>) ((ParameterizedType) method.getGenericReturnType())
                        .getActualTypeArguments()[0];
                if (typeArgument.getAnnotation(JsonDeserialize.class) != null) {
                    List<Map<String, Object>> value = (List<Map<String, Object>>) result;
                    return value.stream().map(asdf -> {
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            return objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(asdf)),
                                    typeArgument);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
                } else if (method.getName().equals("getContainers")) {
                    List<Map<String, Object>> value = (List<Map<String, Object>>) result;
                    return value.stream().map(asdf ->
                            Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                    getImplementedInterfaces(asdf), getInvocationHandler(asdf))).collect(Collectors.toList());
                } else {
                    return null;
                }

            }
            return result;

        };
    }

    public static String propertyName(Method m) {
        String name = m.getName();
        if (name.startsWith("get")) {
            return Introspector.decapitalize(name.substring(3));
        } else if (name.startsWith("is") && m.getReturnType() == Boolean.TYPE) {
            return Introspector.decapitalize(name.substring(2));
        }
        return null;
    }

    Class<?>[] getImplementedInterfaces(Map<String, Object> map) {
        return knownInterfaces.stream().filter(aClass -> Boolean.TRUE.equals(map.get("is" + aClass.getSimpleName())))
                .toArray(Class<?>[]::new);
    }

    private String toJson(Object deployable) throws JsonProcessingException {
        Map<String, Object> map = toMap(deployable);
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }

    private Map<String, Object> toMap(Object deployable) {
        Map<String, Object> map = new HashMap<>();
        Arrays.stream(deployable.getClass().getInterfaces()).filter(aClass -> aClass.getName().startsWith("org.entando")).forEach(aClass ->
                map.put("is" + aClass.getSimpleName(), Boolean.TRUE));
        Arrays.stream(deployable.getClass().getMethods())
                .filter(method -> (method.getName().startsWith("get")
                        || method.getName().startsWith("is") && method.getReturnType() != void.class && method.getParameterCount() == 0))
                .forEach(method -> {
                    try {
                        Object deserialize = deserialize(deployable, method);
                        if (deserialize != null) {
                            map.put(propertyName(method), deserialize);
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                });
        return map;
    }

    private Object deserialize(Object deployable, Method getter)
            throws IllegalAccessException, InvocationTargetException {
        Object value = getter.invoke(deployable);
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        } else if (value instanceof Optional) {
            return ((Optional<?>) value).orElse(null);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().map(o -> {
                if (o.getClass().getAnnotation(JsonDeserialize.class) != null) {
                    return o;
                } else {
                    return toMap(o);
                }
            }).collect(Collectors.toList());
        } else if (value != null && value.getClass().getAnnotation(JsonDeserialize.class) != null) {
            return value;
        }
        return null;
    }
}
