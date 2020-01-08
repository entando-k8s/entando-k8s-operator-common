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
