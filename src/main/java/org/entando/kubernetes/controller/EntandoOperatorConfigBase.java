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

import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;

public abstract class EntandoOperatorConfigBase {

    protected EntandoOperatorConfigBase() {
    }

    protected static String getProperty(EntandoOperatorConfigProperty name, String defaultValue) {
        return lookupProperty(name).orElse(defaultValue);
    }

    public static Optional<String> lookupProperty(EntandoOperatorConfigProperty property) {
        return Optional.ofNullable(lookupProperty(property.getJvmSystemProperty()).orElse(lookupProperty(property.name()).orElse(null)));
    }

    public static Optional<String> lookupProperty(String name) {
        Optional<String> fromEnv = System.getenv().entrySet().stream()
                .filter(entry -> isMatch(name, entry))
                .map(Entry::getValue)
                .findFirst();
        if (fromEnv.isPresent()) {
            return fromEnv;
        } else {
            return System.getProperties().entrySet().stream()
                    .filter(entry -> isMatch(name, entry))
                    .map(Entry::getValue)
                    .map(String.class::cast)
                    .findFirst();
        }
    }

    private static boolean isMatch(String n, Entry<?, ?> entry) {
        if (entry.getValue() == null || ((String) entry.getValue()).trim().isEmpty()) {
            return false;
        }
        String name = n.toLowerCase(Locale.getDefault());
        String key = ((String) entry.getKey()).toLowerCase(Locale.getDefault());
        return name.equals(key) || KubeUtils.snakeCaseOf(name).equals(key);
    }
}
