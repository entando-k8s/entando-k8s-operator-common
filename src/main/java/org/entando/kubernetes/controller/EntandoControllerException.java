package org.entando.kubernetes.controller;

/*
 * Throw this exception to disrupt the current progression of objects being installed.
 */
public class EntandoControllerException extends RuntimeException {

    public EntandoControllerException(String message) {
        super(message);
    }

}
