package org.entando.kubernetes.controller.inprocesstest.argumentcaptors;

import java.util.List;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.mockito.Mockito;
import org.mockito.internal.matchers.CapturingMatcher;
import org.mockito.internal.util.Primitives;

public final class KeycloakClientConfigArgumentCaptor {

    private final CapturingMatcher<KeycloakClientConfig> capturingMatcher;

    private KeycloakClientConfigArgumentCaptor(String clientid) {
        capturingMatcher = new MyCapturingMatcher(clientid);
    }

    public static KeycloakClientConfigArgumentCaptor forClientId(String clientid) {
        return new KeycloakClientConfigArgumentCaptor(clientid);
    }

    public KeycloakClientConfig capture() {
        Mockito.argThat(this.capturingMatcher);
        return Primitives.defaultValue(KeycloakClientConfig.class);
    }

    public KeycloakClientConfig getValue() {
        return this.capturingMatcher.getLastValue();
    }

    public List<KeycloakClientConfig> getAllValues() {
        return this.capturingMatcher.getAllValues();
    }

    public static class MyCapturingMatcher extends CapturingMatcher {

        private final String clientid;

        public MyCapturingMatcher(String clientid) {
            super();
            this.clientid = clientid;
        }

        @Override
        public boolean matches(Object argument) {
            return ((KeycloakClientConfig) argument).getClientId().equals(clientid);
        }
    }
}
