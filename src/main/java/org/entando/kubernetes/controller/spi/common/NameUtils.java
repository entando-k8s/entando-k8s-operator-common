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

package org.entando.kubernetes.controller.spi.common;

import com.google.common.base.Strings;
import java.beans.Introspector;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class NameUtils {

    public static final String RFC_1123_REGEX = "[^a-zA-Z0-9\\-]";
    public static final Pattern RFC_1123_PATTERN = Pattern.compile(RFC_1123_REGEX);

    public static final String URL_KEY = "url";
    public static final String INTERNAL_URL_KEY = "internalUrl";
    public static final String DB_NAME_QUALIFIER = "db";
    public static final String DEFAULT_SERVICE_SUFFIX = "service";
    public static final String DEFAULT_SERVER_QUALIFIER = "server";
    public static final String DEFAULT_INGRESS_SUFFIX = "ingress";
    public static final String MAIN_QUALIFIER = "main";
    public static final String DB_QUALIFIER = "db";
    public static final String SSO_QUALIFIER = "sso";
    public static final String DEFAULT_DEPLOYMENT_SUFFIX = "deployment";
    public static final String DEFAULT_PVC_SUFFIX = "pvc";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern pattern = Pattern.compile("(?=[A-Z][a-z])");
    public static final int GENERIC_K8S_MAX_LENGTH = 63;
    public static final int MAX_LENGTH_OF_DNS_SEGMENT = 63;
    public static final int K8S_DEPLOYMENT_MAX_LENGTH = 253;

    private NameUtils() {
    }

    /**
     * Generates an Entando composite name given its components and the maxSize.
     * If the generated name exceed the #maxSize the baseResourceName is truncated in order to make it fit
     * @param baseResourceName the base name of the resource
     * @param nameQualifier    the middle suffix indicating the deployment
     * @param typeSuffix       the terminator suffix indicating the resource type
     * @param maxLength        the maximum size of the generated name
     * @return the generated name
     */
    public static String generateEntandoResourceName(
            String baseResourceName,
            String nameQualifier,
            String typeSuffix,
            int maxLength) {
        //~
        var sb = new StringBuilder(baseResourceName);

        String completeSuffix = "";
        if (!Strings.isNullOrEmpty(typeSuffix)) {
            completeSuffix = "-" + typeSuffix;
        }

        String completeNameQualifier = "";
        if (!Strings.isNullOrEmpty(nameQualifier)) {
            completeNameQualifier = "-" + nameQualifier;
        }

        int spaceLeft = maxLength - sb.length() - completeNameQualifier.length() - completeSuffix.length();
        if (spaceLeft < 0) {
            // only if it is longer, otherwise \x00 will be added to match the required length
            sb.setLength(sb.length() + spaceLeft);
        }

        sb.append(completeNameQualifier);
        sb.append(completeSuffix);

        return sb.toString();
    }

    public static String shortenLabelToMaxLength(String s) {
        s = truncateStringTo(s, GENERIC_K8S_MAX_LENGTH);
        // try to adjust last char
        if (s.endsWith("-") || s.endsWith("_") || s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String shortenIdentifierTo(String s, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalStateException(String.format("Illegal maxLength %d provided", maxLength));
        }
        s = truncateStringTo(s, maxLength);
        // try to adjust last char
        if (s.endsWith("-") || s.endsWith("_") || s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String truncateStringTo(String s, int maxLength) {
        return (s.length() > maxLength) ? s.substring(0, maxLength) : s;
    }


    /**
     * Useful for labelvalues and container names.
     */
    public static String shortenToMaxLength(String s) {
        return shortenTo(s, GENERIC_K8S_MAX_LENGTH);
    }

    public static String shortenTo(String s, int maxLength) {
        if (s.length() > maxLength) {
            s = s.substring(0, maxLength - 4) + randomNumeric(4);
        }
        return s;
    }

    public static String randomNumeric(int size) {
        String suffix;
        do {
            //+1 to avoid Long.MIN_VALUE that stays negative after Math.abs
            suffix = String.valueOf(Math.abs(SECURE_RANDOM.nextLong() + 1));
        } while (suffix.length() < size);
        return suffix.substring(suffix.length() - size);
    }

    public static String camelCaseToDashDelimited(String in) {
        final String replacement = "-";
        return camelCaseToDelimited(in, replacement).toLowerCase(Locale.ROOT);
    }

    private static String camelCaseToDelimited(String in, String delimiter) {
        return pattern.matcher(Introspector.decapitalize(in)).replaceAll(delimiter);
    }

    public static String snakeCaseOf(String in) {
        return in.replace("-", "_").replace(".", "_");
    }

    public static String controllerNameOf(EntandoCustomResource customResource) {
        return "entando-k8s-" + camelCaseToDashDelimited(customResource.getKind().substring("Entando".length())) + "-controller";
    }

    public static String upperSnakeCaseOf(String camelCase) {
        return camelCaseToDelimited(camelCase, "_").toUpperCase(Locale.ROOT);
    }

    public static String databaseCompliantName(EntandoCustomResource resource, String nameQualifier, DbmsVendorConfig dbmsVendorConfig) {
        StringBuilder idealDatabaseName = new StringBuilder(NameUtils.snakeCaseOf(resource.getMetadata().getName()));
        if (nameQualifier != null) {
            idealDatabaseName.append("_").append(NameUtils.snakeCaseOf(nameQualifier));
        }
        if (idealDatabaseName.length() > dbmsVendorConfig.getMaxDatabaseNameLength()) {
            return idealDatabaseName.substring(0, dbmsVendorConfig.getMaxDatabaseNameLength() - 3) + randomNumeric(3);
        }
        return idealDatabaseName.toString();
    }

    public static String standardIngressName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_INGRESS_SUFFIX;
    }

    public static String standardServiceName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_SERVICE_SUFFIX;
    }

    public static String standardServiceName(EntandoCustomResource resource, String qualifier) {
        if (NameUtils.MAIN_QUALIFIER.equals(qualifier)) {
            return standardServiceName(resource);
        } else {
            if (! ObjectUtils.isEmpty(qualifier)) {
                return resource.getMetadata().getName() + "-" + qualifier + "-" + DEFAULT_SERVICE_SUFFIX;
            } else {
                return resource.getMetadata().getName() + "-" + DEFAULT_SERVICE_SUFFIX;
            }
        }
    }

    public static String standardDeployment(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_DEPLOYMENT_SUFFIX;
    }

    public static String standardPersistentVolumeClaim(EntandoCustomResource resource, String containerQualifier) {
        return resource.getMetadata().getName() + "-" + containerQualifier + "-" + DEFAULT_PVC_SUFFIX;
    }

    public static String lowerDashDelimitedOf(String name) {
        return name.replace("_", "-").toLowerCase(Locale.ROOT);
    }

    public static String standardAdminSecretName(EntandoCustomResource keycloakServer) {
        return keycloakServer.getMetadata().getName() + "-admin-secret";
    }

    public static String makeRfc1123Compatible(String value) {
        return RFC_1123_PATTERN.matcher(value.replace("_", "-")).replaceAll("");
    }
}
