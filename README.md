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
`org.entando.kubernetes.controller.DeployCommand` class. Consumers of this project basically implement a set 
of simple interfaces specified in the package `org.entando.kubernetes.controller.spi`. The pivitol
interface here is the `org.entando.kubernetes.controller.spi.Deployable`, which can be passed as parameter to
the constructor of the `DeployCommand`. The `DeployCommand` then the  uses the implementations of the various
interfaces to construct Kubernetes Deployments, and if the necessary interfaces are implement, also Services 
and Ingresses. It also constructs the various Secrets required by the Deployments produced.
   
Other features offered to consumers such as Entando's Kubernetes controllers include configuration, TLS, 
database and/or database schema creation and preparation of these databases.

Here are some of the key interfaces to implement by consumers 

## org.entando.kubernetes.controller.spi.Deployable  

TBD

## org.entando.kubernetes.controller.spi.Secretive

TBD

## org.entando.kubernetes.controller.spi.TlsAware

TBD

## org.entando.kubernetes.controller.spi.DeployableContainer

TBD

## org.entando.kubernetes.controller.spi.ServiceBackingContainer  

TBD

## org.entando.kubernetes.controller.spi.IngressingDeployable  

TBD

## org.entando.kubernetes.controller.spi.IngressingContainer  

TBD

## org.entando.kubernetes.controller.spi.PublicIngressingDeployable  
TBD

## org.entando.kubernetes.controller.spi.DbAware

TBD
## org.entando.kubernetes.controller.spi.KeycloakAware

TBD

## org.entando.kubernetes.controller.spi.TlsAware

TBD

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
following a similar pattern.

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
  
 

 
 