package org.entando.kubernetes.controller;

public class AutoExit implements Runnable {

    private final boolean exit;
    private int code;

    public AutoExit(boolean exit) {
        this.exit = exit;
    }

    public void run() {
        if (exit) {
            System.exit(code);
        }
    }

    public void withCode(int code) {

        this.code = code;
    }
}
