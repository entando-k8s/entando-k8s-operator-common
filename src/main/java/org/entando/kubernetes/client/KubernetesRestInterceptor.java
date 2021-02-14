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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesRestInterceptor implements InvocationHandler {

    private Object delegate;

    public KubernetesRestInterceptor(Object delegate) {
        this.delegate = delegate;
    }

    private static String format(HasMetadata entandoCustomResource) {
        return String.format(" with %s: %s/%s", entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getNamespace(),
                entandoCustomResource.getMetadata().getName());
    }

    @SuppressWarnings("squid:S2139")//Because it is common practice to log and rethrow an exception in a logging interceptor
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());
        if (logger.isInfoEnabled()) {
            logger.info(buildEnterMessage(method, args));
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            logger.error(String.format("Failure executing method %s in class %s",
                    method.getName(),
                    method.getDeclaringClass().getName()), e.getTargetException());
            throw e.getTargetException();
        } catch (Exception e) {
            logger.error(String.format("Failure executing method %s in class %s",
                    method.getName(),
                    method.getDeclaringClass().getName()), e);
            throw e;
        } finally {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("Exiting method %s in class %s", method.getName(),
                        method.getDeclaringClass().getName()));
            }

        }
    }

    private String buildEnterMessage(Method method, Object[] args) {
        StringBuilder message = new StringBuilder(
                String.format("Entering method %s in class %s", method.getName(),
                        method.getDeclaringClass().getName()));
        if (args != null) {
            Optional<EntandoCustomResource> first = Arrays.stream(args)
                    .filter(EntandoCustomResource.class::isInstance)
                    .map(EntandoCustomResource.class::cast).findFirst();
            first.ifPresent(entandoCustomResource -> message.append(format(entandoCustomResource)));
            Optional<HasMetadata> second = Arrays.stream(args)
                    .filter(o -> o instanceof HasMetadata && o.getClass().getName().startsWith("io.fabric8.kubernetes"))
                    .map(HasMetadata.class::cast).findFirst();
            if (second.isPresent()) {
                if (first.isPresent()) {
                    message.append(" and");
                }
                message.append(format(second.get()));
            }
            if (!(first.isPresent() || second.isPresent())) {
                message.append(" with ")
                        .append(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(",")));
            }
        }
        return message.toString();
    }

}
