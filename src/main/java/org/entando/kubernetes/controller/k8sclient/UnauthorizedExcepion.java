package org.entando.kubernetes.controller.k8sclient;

public class UnauthorizedExcepion extends RuntimeException {

    public UnauthorizedExcepion(String message, Throwable cause) {
        super(message, cause);
    }

}
