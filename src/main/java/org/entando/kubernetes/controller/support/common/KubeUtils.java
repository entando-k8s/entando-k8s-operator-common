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

package org.entando.kubernetes.controller.support.common;

import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public final class KubeUtils {

    public static final String UPDATED_ANNOTATION_NAME = "entando.org/updated";//To avoid  http 400s
    public static final String PROCESSING_INSTRUCTION_ANNOTATION_NAME = "entando.org/processing-instruction";
    public static final String JOB_KIND_LABEL_NAME = "jobKind";
    public static final String DEPLOYMENT_QUALIFIER_LABEL_NAME = "deploymentQualifier";
    public static final String ENTANDO_RESOURCE_ACTION = "entando.resource.action";
    public static final String ENTANDO_RESOURCE_KIND_LABEL_NAME = "EntandoResourceKind";
    public static final String ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME = "EntandoResourceNamespace";
    public static final String ENTANDO_OPERATOR_DEFAULT_CAPABILITIES_CONFIGMAP_NAME = "entando-operator-default-capabilities-config-map";
    public static final String JOB_KIND_DB_PREPARATION = "db-preparation-job";
    public static final String DEPLOYMENT_LABEL_NAME = "deployment";

    private static final Logger LOGGER = Logger.getLogger(KubeUtils.class.getName());

    private KubeUtils() {
    }

    public static String getKindOf(Class<? extends EntandoCustomResource> c) {
        return c.getSimpleName();
    }

    public static void ready(String name) {
        try {
            FileUtils.write(new File("/tmp/" + name + ".ready"), "yes", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not create 'ready' file for {0}", name);
        }
    }

    public static OperatorProcessingInstruction resolveProcessingInstruction(EntandoCustomResource resource) {
        return resolveAnnotation(resource, PROCESSING_INSTRUCTION_ANNOTATION_NAME)
                .map(value -> OperatorProcessingInstruction.valueOf(value.toUpperCase(Locale.ROOT).replace("-", "_")))
                .orElse(OperatorProcessingInstruction.NONE);
    }

    public static Optional<String> resolveAnnotation(EntandoCustomResource resource, String name) {
        return ofNullable(resource.getMetadata().getAnnotations()).map(map -> map.get(name));
    }

    public static EntandoControllerFailure failureOf(EntandoCustomResource r, Exception e) {
        final StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        final String failedObjectName = r.getMetadata().getNamespace() + "/" + r.getMetadata().getName();
        return new EntandoControllerFailure(r.getKind(), failedObjectName, e.getMessage(), stringWriter.toString());
    }
}
