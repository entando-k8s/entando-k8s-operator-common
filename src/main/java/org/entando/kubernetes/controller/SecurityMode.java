package org.entando.kubernetes.controller;

import java.util.Locale;

public enum SecurityMode {
    STRICT, LENIENT;

    public static SecurityMode caseInsensitiveValueOf(String securityMode) {
        return valueOf(securityMode.toUpperCase(Locale.getDefault()));
    }
}
