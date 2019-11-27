package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.entando.kubernetes.controller.k8sclient.DuplicateExcepion;
import org.entando.kubernetes.controller.k8sclient.UnauthorizedExcepion;
import org.entando.kubernetes.model.EntandoCustomResource;

public final class KubernetesExceptionProcessor {

    private static final int UNAUTHORIZED = 403;
    private static final int CONFLICT = 409;

    private KubernetesExceptionProcessor() {
    }

    public static <T extends HasMetadata> T verifyDuplicateExceptionOnCreate(String controllerNamespace, T resource,
            KubernetesClientException e) {
        if (e.getCode() == UNAUTHORIZED) {
            throw new UnauthorizedExcepion(String.format(
                    "Could not create a %s in namespace %s. If you are running the Entando controllers in STRICT security mode, please "
                            + "check  that you have associated the entando-k8s-infra service account with a Role that allows the 'CREATE' "
                            + "verb against resources of kind %s ",
                    resource.getKind(), controllerNamespace, resource.getKind()), e);
        } else if (e.getCode() == CONFLICT) {
            throw new DuplicateExcepion(String.format(
                    "Could not create a %s named '%s'in namespace %s because it already exists. This could be because it has already "
                            + "been created as part of the setup. Please verify that the Kubernetes infrastructure represented "
                            + "by this specific %s is in tact. If not, please delete the existing %s and run the command again.",
                    resource.getKind(), resource.getMetadata().getName(), controllerNamespace, resource.getKind(), resource.getKind()), e);
        } else {
            throw e;
        }
    }

    public static <T extends HasMetadata> T squashDuplicateExceptionOnCreate(EntandoCustomResource peerInNamespace, T resource,
            KubernetesClientException e) {
        if (e.getCode() == UNAUTHORIZED) {
            throw new UnauthorizedExcepion(String.format(
                    "Could not create a %s in namespace %s. If you are running the Entando controllers in STRICT security mode,  please "
                            + "check that you have associated the entando-k8s-infra service account with a Role that allows the 'CREATE' "
                            + "verb against resources of kind %s ",
                    resource.getKind(), peerInNamespace.getMetadata().getNamespace(), resource.getKind()), e);
        } else if (e.getCode() == CONFLICT) {
            //We're generally interested in its name here. 409 guarantees the name already exists, and it is safe to bind to.
            //The client code should not return this object if assumptions have been made about its state
            //TODO maybe only return the name?

            return resource;
        } else {
            throw e;
        }
    }

    public static RuntimeException processExceptionOnLoad(EntandoCustomResource peerInNamespace, KubernetesClientException e, String kind,
            String name) {
        if (e.getCode() == UNAUTHORIZED) {
            return new UnauthorizedExcepion(String.format(
                    "Could not load %s named %s in namespace %s. If you are running the Entando controllers in STRICT security mode, "
                            + "please check  that you have associated the entando-k8s-infra service account with a Role that allows the "
                            + "'GET' verb against resources of kind %s ",
                    kind, name, peerInNamespace.getMetadata().getNamespace(), kind), e);
        } else {
            return e;
        }
    }

}
