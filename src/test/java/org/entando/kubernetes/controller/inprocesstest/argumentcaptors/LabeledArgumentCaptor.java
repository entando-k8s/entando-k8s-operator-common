package org.entando.kubernetes.controller.inprocesstest.argumentcaptors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.mockito.Mockito;
import org.mockito.internal.matchers.CapturingMatcher;
import org.mockito.internal.util.Primitives;

public final class LabeledArgumentCaptor<T extends HasMetadata> {

    private final Class<? extends T> clazz;
    private final Map<String, String> labelsToMatch = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    private final CapturingMatcher<T> capturingMatcher = new CapturingMatcher() {
        @Override
        public boolean matches(Object argument) {
            return labelsToMatch.entrySet().stream().allMatch(getIsPresentMapPredicate((HasMetadata) argument));
        }

        protected Predicate<Map.Entry<String, String>> getIsPresentMapPredicate(HasMetadata argument) {
            return entry -> argument.getMetadata().getLabels().containsKey(entry.getKey()) && argument.getMetadata()
                    .getLabels().get(entry.getKey()).equals(entry.getValue());
        }
    };

    private LabeledArgumentCaptor(Class<? extends T> clazz, String labelName, String labelValue) {
        this.clazz = clazz;
        andWithLabel(labelName, labelValue);
    }

    @SuppressWarnings("unchecked")
    public static <U extends HasMetadata, S extends U> LabeledArgumentCaptor<U> forResourceWithLabel(Class<S> clazz,
            String labelname, String labelValue) {
        return new LabeledArgumentCaptor(clazz, labelname, labelValue);
    }

    public LabeledArgumentCaptor<T> andWithLabel(String labelName, String labelValue) {
        labelsToMatch.put(labelName, labelValue);
        return this;
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
}
