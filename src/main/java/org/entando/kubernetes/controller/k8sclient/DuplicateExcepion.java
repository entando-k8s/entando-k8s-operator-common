package org.entando.kubernetes.controller.k8sclient;

public class DuplicateExcepion extends RuntimeException {

    public DuplicateExcepion(String message, Throwable cause) {
        super(message, cause);
    }

}
