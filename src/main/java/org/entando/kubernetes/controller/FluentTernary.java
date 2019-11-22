package org.entando.kubernetes.controller;

public class FluentTernary<T> {

    private final T trueValue;
    private boolean condition;

    public FluentTernary(T trueValue) {
        this.trueValue = trueValue;
    }

    public static <T> FluentTernary<T> use(T trueValue) {
        return new FluentTernary<>(trueValue);
    }

    public static <T> FluentTernary<T> useNull(Class<T> clazz) {

        return new FluentTernary<>(clazz.cast(null));
    }

    public FluentTernary<T> when(boolean condition) {
        this.condition = condition;
        return this;
    }

    public T orElse(T falseValue) {
        return condition ? trueValue : falseValue;
    }
}
