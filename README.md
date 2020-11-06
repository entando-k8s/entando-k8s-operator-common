[![Build Status](https://img.shields.io/endpoint?url=https%3A%2F%2Fstatusbadge-jx.apps.serv.run%2Fentando-k8s%2Fentando-k8s-operator-common)](https://github.com/entando-k8s/devops-results/tree/logs/jenkins-x/logs/entando-k8s/entando-k8s-operator-common/master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=alert_status)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=coverage)](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/master/jacoco/index.html)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=vulnerabilities)](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/master/dependency-check-report.html)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=code_smells)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=security_rating)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=sqale_index)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)


# Entando Kubernetes Operator Common

This project provides a library that can be used to facilitate the development of run-to-completion style
Kubernetes controllers. Most of the logic in this project is co-ordinated by the 
`org.entando.kubernetes.controller.IngressingDeployCommand` class. Consumers of this project basically implement a set 
of simple interfaces specified in the package `org.entando.kubernetes.controller.spi`. The pivitol
interface here is the `org.entando.kubernetes.controller.spi.Deployable`, which can be passed as parameter to
the constructor of the `DeployCommand`. The `DeployCommand` then uses the implementations of the various
interfaces to construct Kubernetes Deployments, and if the necessary interfaces are implement, also Services 
and Ingresses. It also constructs the various Secrets required by the Deployments produced.
   
Other features offered to consumers such as Entando's Kubernetes controllers include configuration, TLS, 
database and/or database schema creation and preparation of these databases.

Here are some of the key interfaces to implement by consumers 

### org.entando.kubernetes.controller.spi.Deployable  

This is a generic interface that represents a common deployable pod. As a pod, it has a list of containers (represented by a list of DeployableContainer).
There are some classes implementing the Deployable interface, representing each one a different type of deployable (e.g. DatabaseDeployable, PublicIngressingDeployable)
The DeploymentCreator class is responsible to create Deployable instances.

### org.entando.kubernetes.controller.spi.Secretive

This interface has to be implemented by those Deployable that need Kubernetes secrets for working.
Currently all you have to do with this interface is to override `buildSecrets()` method returning all needed secrets and they will be bound to the pod that is about to be deployed.

### org.entando.kubernetes.controller.spi.TlsAware

This interface offers a predefined way to add some TLS environment variable to the implementing DeployableContainer.

### org.entando.kubernetes.controller.spi.DeployableContainer

Base interface representing a container to deploy inside a Pod. So it needs at least:

- the docker image of which instantiate the container
- a name qualifier to append to the container name
- the port exposed by the container

### org.entando.kubernetes.controller.spi.ServiceBackingContainer  

TBD

### org.entando.kubernetes.controller.spi.IngressingDeployable  

This interface has to be implemented by those Deployable that contain some IngressingContainer inside their containers list.
Its main functionality is to easily retrieve the IngressingContainer list of a Deployable.
Read more about Kubernetes [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### org.entando.kubernetes.controller.spi.IngressingContainer  

IngressingContainer is an interface representing a container that supply ingress functionalities.
Read more about Kubernetes [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### org.entando.kubernetes.controller.spi.PublicIngressingDeployable  

TBD

### org.entando.kubernetes.controller.spi.DbAware

This interface has to be implemented by those `DeployableContainer` that needs a DB to serve their functionalities.
It takes care of:

- establish a connection between the implementing `DeployableContainer` and the DBMS instance identified by the environment variables supplied in the `addDatabaseConnectionVariables()` method. You can see an example in the [KeycloakDeployableContainer](https://github.com/entando-k8s/entando-k8s-keycloak-controller/blob/master/src/main/java/org/entando/kubernetes/controller/keycloakserver/KeycloakDeployableContainer.java) 
- create the desired DB schema
- populate the created DB schema if a DatabasePopulator is returned by the implemented `useDatabaseSchemas()` method

Entando supports these DBMS: `H2`, `Postgresql`, `MySQL`, `Oracle`. You can choose which one to use for each `DeployableContainer` implementing `DbAware` interface by specifying a `spec.dbms` property in the Kubernetes deployment file.
You can find more info in the [Getting started](http://docs.entando.com/local-install.html#dbms)

### org.entando.kubernetes.controller.spi.PersistentVolumeAware

This interface has to be implemented by those DeployableContainer that needs a [PVC](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#persistentvolumeclaims) in order to persist some data.
This interface has only one method to be overridden: `getVolumeMountPath()`. It returns the path of the volume to claim inside the container that is about to be created.
Once overridden that method, claim operation is automatically made, the PVC is bound to the claimer [CR](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) and once the owner CR is deleted the PVC is deleted too.

### org.entando.kubernetes.controller.spi.KeycloakAware

This interface has to be implemented by those DeployableContainer that needs to reach Keycloak to guarantee their functionalities.
It comes with a predefined set of environment variables pointing to the Entando default Keycloak installation.

## Creators

There are a lot of creator classes, each one responsible for the creation of something in particular.
When a `DeployCommand` needs to create something (like a PVC, a Secret, an Ingress, etc.) it asks for creation to the related creator.
The notified creator optionally processes some business logic and then delegates to the related [client](#interactions-with-clusters) the creation of the desired object into the Kubernetes cluster.

## Interactions with clusters

Because of the main goal of this project is to supply a unified interface to interact with Kubernetes clusters, the class `org.entando.kubernetes.client.DefaultSimpleK8SClient` is another pivotal component.
In contains a list of clients, each one leveraging Fabric8's `KubernetesClient` in order to supply a "separated by concerns" cluster interaction interface.

So in particolar

# Configuration

Like most Kubernetes applications, this library uses configuration data from four difference sources: 
Kubernetes Secrets and Configmaps,  OS Environment variables and JVM System Properties. OS Environment Variables
override equivalent JVM System Properties. JVM System Properties and OS Environment Variables are 
considered equivalent if the OS Environment Variable is the uppercase snake case equivalent of the JVM System Property
in lowercase, dot-delimited. For instance, the OS Environment Variable `ENTANDO_CA_CERT_PATHS` would override the
JVM System Property `entando.ca.cert.paths`
 
## How it resolves Docker images

In order to support development of both Controller images and supporting images such as sidecars, we have established
a very flexible Docker image resolution process. The eventual image URI that we resolve has 4 segments to it: 
 * a Docker registry
 * a Docker (or openshift) registry namespace
 * the image name
 * a version suffix.
Entando looks for default and overriding configuration settings in both the OS Environment Variables and JVM System Properties
to resolve the full image URI. It also inspects a Kubernetes ConfigMap named `entando-image-versions`, which stores 
a JSON formatted image configuration against known image names. This ConfigMap can be created in the Operator's namespace.
For images in the `entando` namespace, the registry, namespace and version segments of the image URI can be overridden 
following a similar pattern. (The namespace used to resolve this ConfigMap can be overridden by using the `ENTANDO_K8S_OPERATOR_CONFIGMAP_NAMESPACE` environment variable/system property)

The Docker registry segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_REGISTRY_OVERRIDE=docker.io., was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{docker-registry:docker.io}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_REGISTRY_DEFAULT=docker.io, will be used if nothing else was specified.

The Docker namespace segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_OVERRIDE=test-namespace, was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{image-namespace:test-namespace}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT=test-namespace, will be used if nothing else was specified.

The image version segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE=6.0.14, was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{version:6.0.14}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT=6.0.14, will be used if nothing else was specified.

## Configuring TLS settings

TBD
  
## Examples

Some examples are available in the test package at the location `org.entando.kubernetes.controller.common.examples`