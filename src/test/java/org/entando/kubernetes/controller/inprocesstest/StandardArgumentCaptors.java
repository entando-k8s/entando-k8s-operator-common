package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.api.model.HasMetadata;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.KeycloakClientConfigArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;

public interface StandardArgumentCaptors {

    default KeycloakClientConfigArgumentCaptor forClientId(String clientid) {
        return KeycloakClientConfigArgumentCaptor.forClientId(clientid);
    }

    default <U extends HasMetadata, S extends U> LabeledArgumentCaptor<U> forResourceWithLabel(Class<S> clazz,
            String labelname, String labelValue) {
        return LabeledArgumentCaptor.forResourceWithLabel(clazz, labelname, labelValue);
    }

    default <U extends HasMetadata, S extends U> NamedArgumentCaptor<U> forResourceNamed(Class<S> clazz,
            String name) {
        return NamedArgumentCaptor.forResourceNamed(clazz, name);
    }

}
