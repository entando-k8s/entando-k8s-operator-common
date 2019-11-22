package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.model.EntandoCustomResource;

public class AbstractK8SResourceCreator {

    protected final EntandoCustomResource entandoCustomResource;

    public AbstractK8SResourceCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    protected String resolveName(String nameQualifier, String suffix) {
        return entandoCustomResource.getMetadata().getName() + "-" + nameQualifier + suffix;
    }

    protected ObjectMeta fromCustomResource(boolean ownedByCustomResource, String name, String nameQualifier) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource(nameQualifier));
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(KubeUtils.buildOwnerReference(this.entandoCustomResource));
        }
        return metaBuilder.build();
    }

    protected ObjectMeta fromCustomResource(boolean ownedByCustomResource, String name) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                .withLabels(labelsFromResource());
        if (ownedByCustomResource) {
            metaBuilder = metaBuilder.withOwnerReferences(KubeUtils.buildOwnerReference(this.entandoCustomResource));
        }
        return metaBuilder.build();
    }

    protected Map<String, String> labelsFromResource(String nameQualifier) {
        Map<String, String> labels = new ConcurrentHashMap<>();
        labels.put(DeployCommand.DEPLOYMENT_LABEL_NAME, resolveName(nameQualifier, ""));
        resourceKindLabels(labels);
        return labels;
    }

    protected Map<String, String> labelsFromResource() {
        Map<String, String> labels = new ConcurrentHashMap<>();
        resourceKindLabels(labels);
        return labels;
    }

    private void resourceKindLabels(Map<String, String> labels) {
        labels.put(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName());
        labels.put(DeployCommand.DEPLOYMENT_LABEL_KIND, entandoCustomResource.getKind());
    }
}
