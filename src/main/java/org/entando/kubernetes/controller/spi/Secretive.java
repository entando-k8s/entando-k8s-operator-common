package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.List;

public interface Secretive {

    List<Secret> buildSecrets();
}
