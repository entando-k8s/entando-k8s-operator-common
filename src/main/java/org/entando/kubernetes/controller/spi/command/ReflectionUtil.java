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

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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

public class ReflectionUtil {

    public static final List<Class<?>> KNOWN_INTERFACES = Arrays.asList(
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

    private ReflectionUtil() {

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

    public static <A extends Annotation> A getAnnotation(Class<?> c, String methodName, Class<A> annotationType) {
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

    public static <A extends Annotation> A getAnnotationFromInterfaces(Class<?>[] interfaces, String methodName,
            Class<A> annotationType) {
        for (Class<?> intf : interfaces) {
            A annotation = getAnnotation(intf, methodName, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

}
