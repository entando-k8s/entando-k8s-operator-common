package org.entando.kubernetes.controller;

import java.util.Locale;

public enum OperatorScope {
    CLUSTER, NAMESPACE;

    public static OperatorScope caseInsensitiveValueOf(String scope) {
        return valueOf(scope.toUpperCase(Locale.getDefault()));
    }
}
