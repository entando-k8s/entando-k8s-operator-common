package org.entando.kubernetes.controller.inprocesstest.argumentcaptors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.List;
import org.mockito.Mockito;
import org.mockito.internal.matchers.CapturingMatcher;
import org.mockito.internal.util.Primitives;

public final class NamedArgumentCaptor<T extends HasMetadata> {

    private final Class<? extends T> clazz;
    private final CapturingMatcher<T> capturingMatcher;

    @SuppressWarnings("unchecked")
    private NamedArgumentCaptor(Class<? extends T> clazz, String name) {
        this.clazz = clazz;
        capturingMatcher = new MyCapturingMatcher(name);
    }

    @SuppressWarnings("unchecked")
    public static <U extends HasMetadata, S extends U> NamedArgumentCaptor<U> forResourceNamed(Class<S> clazz,
            String name) {
        return new NamedArgumentCaptor(clazz, name);
    }

    public T capture() {
        Mockito.argThat(this.capturingMatcher);
        return Primitives.defaultValue(this.clazz);
    }

    public T getValue() {
        return this.capturingMatcher.getLastValue();
    }

    public List<T> getAllValues() {
        return this.capturingMatcher.getAllValues();
    }

    public class MyCapturingMatcher extends CapturingMatcher {

        private final String name;

        public MyCapturingMatcher(String name) {
            super();
            this.name = name;
        }

        @Override
        public boolean matches(Object argument) {
            return ((HasMetadata) argument).getMetadata().getName().equals(name);
        }
    }
}
