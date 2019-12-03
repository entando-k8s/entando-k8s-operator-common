package org.entando.kubernetes.controller.creators;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.k8sclient.DeploymentClient;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.HasHealthCommand;
import org.entando.kubernetes.controller.spi.HasWebContext;
import org.entando.kubernetes.controller.spi.KeycloakAware;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.TlsAware;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DeploymentCreator extends AbstractK8SResourceCreator {

    public static final String ENTANDO_CONNECTIONS_ROOT_VALUE = "/etc/entando/connectionconfigs";
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String DEFAULT_TRUST_STORE_SECRET_NAME = "entando-default-trust-store-secret";
    private static final String VOLUME_SUFFIX = "-volume";
    private static final String KEYSTORES_ROOT = "/etc/entando/keystores";
    public static final String TRUST_STORE_PATH = standardCertPathOf(TRUST_STORE_FILE);
    private Deployment deployment;

    public DeploymentCreator(
            EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public static String standardCertPathOf(String filename) {
        return String.format("%s/%s/%s", KEYSTORES_ROOT, DEFAULT_TRUST_STORE_SECRET_NAME, filename);
    }

    public Deployment createDeployment(DeploymentClient deploymentClient,
            Deployable deployable) {
        deployment = deploymentClient
                .createDeployment(entandoCustomResource, newDeployment(deployable));
        return deployment;
    }

    public DeploymentStatus reloadDeployment(DeploymentClient deployments) {
        if (deployment == null) {
            return null;
        }
        deployment = deployments.loadDeployment(entandoCustomResource, deployment.getMetadata().getName());
        return deployment.getStatus();
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private DeploymentSpec buildDeploymentSpec(Deployable<?> deployable) {
        return new DeploymentBuilder()
                .withNewSpec()
                .withNewSelector()
                .withMatchLabels(labelsFromResource(deployable.getNameQualifier()))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withName(resolveName(deployable.getNameQualifier(), "-pod"))
                .withLabels(labelsFromResource(deployable.getNameQualifier()))
                .endMetadata()
                .withNewSpec()
                .withContainers(buildContainers(deployable))
                .withDnsPolicy("ClusterFirst")
                .withRestartPolicy("Always")
                .withServiceAccountName(deployable.getServiceAccountName())
                .withVolumes(buildVolumesForDeployable(deployable)).endSpec()
                .endTemplate()
                .endSpec().buildSpec();
    }

    private List<Volume> buildVolumesForDeployable(Deployable<?> deployable) {
        List<Volume> volumeList = deployable.getContainers().stream()
                .map(this::buildVolumesForContainer)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (deployable.getContainers().stream().anyMatch(TlsAware.class::isInstance) && TlsHelper.getInstance().isTrustStoreAvailable()) {
            volumeList.add(newSecretVolume(DEFAULT_TRUST_STORE_SECRET_NAME));
        }
        return volumeList;
    }

    private List<Volume> buildVolumesForContainer(DeployableContainer container) {
        List<Volume> volumes = new ArrayList<>();
        if (container instanceof PersistentVolumeAware) {
            volumes.add(new VolumeBuilder()
                    .withName(volumeName(container))
                    .withNewPersistentVolumeClaim(resolveName(container.getNameQualifier(), "-pvc"), false)
                    .build());
        }
        volumes.addAll(container.getConnectionConfigNames().stream()
                .map(this::newSecretVolume)
                .collect(Collectors.toList()));
        return volumes;
    }

    private Volume newSecretVolume(String s) {
        return new VolumeBuilder()
                .withName(s + VOLUME_SUFFIX)
                .withNewSecret()
                .withSecretName(s)
                .endSecret()
                .build();
    }

    private List<Container> buildContainers(Deployable<?> deployable) {
        return deployable.getContainers().stream().map(this::newContainer)
                .collect(Collectors.toList());
    }

    private Container newContainer(DeployableContainer deployableContainer) {
        return new ContainerBuilder().withName(resolveName(deployableContainer.getNameQualifier(), "-container"))
                .withImage(deployableContainer.determineImageToUse())
                .withImagePullPolicy("Always")
                .addNewPort().withName(deployableContainer.getNameQualifier() + "-port")
                .withContainerPort(deployableContainer.getPort()).withProtocol("TCP").endPort()
                .withReadinessProbe(buildReadinessProbe(deployableContainer))
                .withVolumeMounts(buildVolumeMounts(deployableContainer))
                .withEnv(determineEnvironmentVariables(deployableContainer))
                .withNewResources()
                .addToLimits("memory", new Quantity("2048Mi")).addToRequests("memory", new Quantity("256Mi"))
                .endResources()
                .build();
    }

    private List<VolumeMount> buildVolumeMounts(DeployableContainer deployableContainer) {
        List<VolumeMount> volumeMounts = new ArrayList<>(
                deployableContainer.getConnectionConfigNames().stream()
                        .map(this::newSecretVolumeMount)
                        .collect(Collectors.toList()));
        if (deployableContainer instanceof TlsAware && TlsHelper.getInstance().isTrustStoreAvailable()) {
            volumeMounts.add(new VolumeMountBuilder()
                    .withName(DEFAULT_TRUST_STORE_SECRET_NAME + VOLUME_SUFFIX)
                    .withMountPath("/etc/entando/keystores/" + DEFAULT_TRUST_STORE_SECRET_NAME).withReadOnly(true).build());
        }
        if (deployableContainer instanceof PersistentVolumeAware) {
            String volumeMountPath = ((PersistentVolumeAware) deployableContainer).getVolumeMountPath();
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withMountPath(volumeMountPath)
                            .withName(volumeName(deployableContainer))
                            .withReadOnly(false).build());
        }
        return volumeMounts;

    }

    private VolumeMount newSecretVolumeMount(String s) {
        return new VolumeMountBuilder()
                .withName(s + VOLUME_SUFFIX)
                .withMountPath(ENTANDO_CONNECTIONS_ROOT_VALUE + "/" + s).withReadOnly(true).build();
    }

    private Probe buildReadinessProbe(DeployableContainer deployableContainer) {
        Probe probe = null;
        if (deployableContainer instanceof HasHealthCommand) {
            probe = new ProbeBuilder().withNewExec().addToCommand("/bin/sh", "-i", "-c",
                    ((HasHealthCommand) deployableContainer).getHealthCheckCommand()).endExec()
                    .withPeriodSeconds(3)
                    .withInitialDelaySeconds(10)
                    .withTimeoutSeconds(1)
                    .withFailureThreshold(40)
                    .build();
        } else if (deployableContainer instanceof HasWebContext) {
            Optional<String> healthCheckPath = ((HasWebContext) deployableContainer).getHealthCheckPath();
            if (healthCheckPath.isPresent()) {
                probe = new ProbeBuilder().withNewHttpGet().withNewPort(deployableContainer.getPort())
                        .withPath(healthCheckPath.get()).endHttpGet()
                        .withPeriodSeconds(6)
                        .withInitialDelaySeconds(30)
                        .withTimeoutSeconds(3)
                        .withFailureThreshold(40)
                        .build();
            }
        } else {
            probe = new ProbeBuilder().withNewTcpSocket().withNewPort(deployableContainer.getPort())
                    .withHost("localhost").endTcpSocket()
                    .build();
        }
        return probe;
    }

    private List<EnvVar> determineEnvironmentVariables(DeployableContainer container) {
        ArrayList<EnvVar> vars = new ArrayList<>();
        if (container instanceof KeycloakAware) {
            KeycloakAware keycloakAware = (KeycloakAware) container;
            KeycloakConnectionConfig keycloakDeployment = keycloakAware.getKeycloakDeploymentResult();
            vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
            vars.add(new EnvVar("KEYCLOAK_REALM", KubeUtils.ENTANDO_KEYCLOAK_REALM, null));
            vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", KubeUtils.PUBLIC_CLIENT_ID, null));
            vars.add(new EnvVar("KEYCLOAK_AUTH_URL", keycloakDeployment.getBaseUrl(), null));
            String keycloakSecretName = KeycloakClientCreator.keycloakClientSecret(keycloakAware.getKeycloakConfig());
            vars.add(new EnvVar("KEYCLOAK_CLIENT_SECRET", null,
                    KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_SECRET_KEY)));
            vars.add(new EnvVar("KEYCLOAK_CLIENT_ID", null,
                    KubeUtils.secretKeyRef(keycloakSecretName, KeycloakClientCreator.CLIENT_ID_KEY)));
        }
        if (container instanceof HasWebContext) {
            vars.add(new EnvVar("SERVER_SERVLET_CONTEXT_PATH", ((HasWebContext) container).getWebContextPath(), null));
        }
        if (container instanceof TlsAware && TlsHelper.getInstance().isTrustStoreAvailable()) {
            ((TlsAware) container).addTlsVariables(vars);
        }
        vars.add(new EnvVar("CONNECTION_CONFIG_ROOT", ENTANDO_CONNECTIONS_ROOT_VALUE, null));
        container.addEnvironmentVariables(vars);
        return vars;
    }

    private String volumeName(DeployableContainer container) {
        return resolveName(container.getNameQualifier(), VOLUME_SUFFIX);
    }

    protected Deployment newDeployment(Deployable deployable) {
        return new DeploymentBuilder()
                .withMetadata(fromCustomResource(true, resolveName(((Deployable<?>) deployable).getNameQualifier(), "-deployment"),
                        ((Deployable<?>) deployable).getNameQualifier()))
                .withSpec(buildDeploymentSpec(deployable))
                .build();
    }

}
