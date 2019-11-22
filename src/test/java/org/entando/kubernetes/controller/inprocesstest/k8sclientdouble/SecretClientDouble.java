package org.entando.kubernetes.controller.inprocesstest.k8sclientdouble;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Map;
import org.entando.kubernetes.controller.k8sclient.SecretClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public class SecretClientDouble extends AbstractK8SClientDouble implements SecretClient {

    public SecretClientDouble(Map<String, NamespaceDouble> namespaces) {
        super(namespaces);
    }

    @Override
    public void overwriteControllerSecret(Secret secret) {
        getNamespaces().get(CONTROLLER_NAMESPACE).putSecret(secret);
    }

    @Override
    public void createSecretIfAbsent(EntandoCustomResource peerInNamespace, Secret secret) {
        getNamespace(peerInNamespace).putSecret(secret);
    }

    @Override
    public Secret loadSecret(EntandoCustomResource resource, String secretName) {
        return getNamespace(resource).getSecret(secretName);
    }
}
