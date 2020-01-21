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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.EntandoImageResolver;
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

    public static final String ENTANDO_SECRET_MOUNTS_ROOT = "/etc/entando/connectionconfigs";
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String VOLUME_SUFFIX = "-volume";
    public static final String DEPLOYMENT_SUFFIX = "-deployment";
    public static final String CONTAINER_SUFFIX = "-container";
    public static final String PORT_SUFFIX = "-port";
    public static final String CERT_SECRET_MOUNT_ROOT = "/etc/entando/certs";
    public static final String TRUST_STORE_PATH = standardCertPathOf(TRUST_STORE_FILE);
    private Deployment deployment;

    public DeploymentCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public static String standardCertPathOf(String filename) {
        return String.format("%s/%s/%s", CERT_SECRET_MOUNT_ROOT, SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME, filename);
    }

    public Deployment createDeployment(EntandoImageResolver imageResolver, DeploymentClient deploymentClient, Deployable deployable) {
        deployment = deploymentClient
                .createDeployment(entandoCustomResource, newDeployment(imageResolver, deployable));
        return deployment;
    }

    public DeploymentStatus reloadDeployment(DeploymentClient deployments) {
        if (deployment == null) {
            return null;
        }
        deployment = deployments.loadDeployment(entandoCustomResource, deployment.getMetadata().getName());
        return deployment.getStatus() == null ? new DeploymentStatus() : deployment.getStatus();
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private DeploymentSpec buildDeploymentSpec(EntandoImageResolver imageResolver,
            Deployable<?> deployable) {
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
                .withContainers(buildContainers(imageResolver, deployable))
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
            volumeList.add(newSecretVolume(SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME));
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
        volumes.addAll(container.getNamesOfSecretsToMount().stream()
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

    private List<Container> buildContainers(EntandoImageResolver imageResolver, Deployable<?> deployable) {
        return deployable.getContainers().stream().map(deployableContainer -> this.newContainer(imageResolver, deployableContainer))
                .collect(Collectors.toList());
    }

    private Container newContainer(EntandoImageResolver imageResolver,
            DeployableContainer deployableContainer) {
        return new ContainerBuilder().withName(deployableContainer.getNameQualifier() + CONTAINER_SUFFIX)
                .withImage(imageResolver.determineImageUri(deployableContainer.determineImageToUse(), Optional.empty()))
                .withImagePullPolicy("Always")
                .addNewPort().withName(deployableContainer.getNameQualifier() + PORT_SUFFIX)
                .withContainerPort(deployableContainer.getPort()).withProtocol("TCP").endPort()
                .withReadinessProbe(buildReadinessProbe(deployableContainer))
                .withVolumeMounts(buildVolumeMounts(deployableContainer))
                .withEnv(determineEnvironmentVariables(deployableContainer))
                .withNewResources()
                .addToLimits(buildResourceLimits(deployableContainer))
                .addToRequests(buildResourceRequests(deployableContainer))
                .endResources()
                .build();
    }

    private Map<String, Quantity> buildResourceRequests(DeployableContainer deployableContainer) {
        Map<String, Quantity> result = new ConcurrentHashMap<>();
        result.put("memory", new Quantity((deployableContainer.getMemoryLimitMebibytes() / 4) + "Mi"));
        result.put("cpu", new Quantity((deployableContainer.getCpuLimitMillicores() / 4) + "m"));
        return result;
    }

    private Map<String, Quantity> buildResourceLimits(DeployableContainer deployableContainer) {
        Map<String, Quantity> result = new ConcurrentHashMap<>();
        result.put("memory", new Quantity(deployableContainer.getMemoryLimitMebibytes() + "Mi"));
        result.put("cpu", new Quantity(deployableContainer.getCpuLimitMillicores() + "m"));
        return result;
    }

    private List<VolumeMount> buildVolumeMounts(DeployableContainer deployableContainer) {
        List<VolumeMount> volumeMounts = new ArrayList<>(
                deployableContainer.getNamesOfSecretsToMount().stream()
                        .map(this::newSecretVolumeMount)
                        .collect(Collectors.toList()));
        if (deployableContainer instanceof TlsAware && TlsHelper.getInstance().isTrustStoreAvailable()) {
            volumeMounts.add(new VolumeMountBuilder()
                    .withName(SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME + VOLUME_SUFFIX)
                    .withMountPath(CERT_SECRET_MOUNT_ROOT + "/" + SecretCreator.DEFAULT_CERTIFICATE_AUTHORITY_SECRET_NAME)
                    .withReadOnly(true).build());
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
                .withMountPath(ENTANDO_SECRET_MOUNTS_ROOT + "/" + s).withReadOnly(true).build();
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
            KeycloakConnectionConfig keycloakDeployment = keycloakAware.getKeycloakConnectionConfig();
            vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
            vars.add(new EnvVar("KEYCLOAK_REALM", KubeUtils.ENTANDO_KEYCLOAK_REALM, null));
            vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", KubeUtils.PUBLIC_CLIENT_ID, null));
            vars.add(new EnvVar("KEYCLOAK_AUTH_URL", keycloakDeployment.getBaseUrl(), null));
            String keycloakSecretName = KeycloakClientCreator.keycloakClientSecret(keycloakAware.getKeycloakClientConfig());
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
        vars.add(new EnvVar("CONNECTION_CONFIG_ROOT", ENTANDO_SECRET_MOUNTS_ROOT, null));
        container.addEnvironmentVariables(vars);
        return vars;
    }

    private String volumeName(DeployableContainer container) {
        return resolveName(container.getNameQualifier(), VOLUME_SUFFIX);
    }

    protected Deployment newDeployment(EntandoImageResolver imageResolver, Deployable<?> deployable) {
        return new DeploymentBuilder()
                .withMetadata(fromCustomResource(true, resolveName(deployable.getNameQualifier(), DEPLOYMENT_SUFFIX),
                        deployable.getNameQualifier()))
                .withSpec(buildDeploymentSpec(imageResolver, deployable))
                .build();
    }

}
