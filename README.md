# Spinnaker Kustomize

Minimal Spinnaker installation optimized for new users and as a base for
extension.

## Prerequisites

A Kubernetes cluster with 16GB of available memory and `kubectl`
configured to communicate with the cluster.

Or, for testing you can create a development cluster with `Kind` using the
commands below.

WARNING: The default configuration grants Spinnaker `cluster-admin` privileges
and does not enable any authentication or authorization to prevent malicious
use. Please only install into a private development cluster.

## Quick start

If required, start a local [https://kind.sigs.k8s.io/](KinD) cluster:

```
make create

# kind create cluster --name spinnaker --config kind.yml
```

Generate Kubernetes yaml:

```
make build

# kubectl kustomize -o ./spinnaker.yaml
```

Check what Kubernetes cluster are pointing to:

```
kubectl config current-context
```

Install Spinnaker into the cluster:

```
make apply

# kubectl apply -f ./spinnaker.yaml
```

Pods will crash loop until MariaDB and Redis are available.

## Customizing Spinnaker

Production workloads require higher reliability and scale than the default
configuration will support. See [spinnaker.io](https://spinnaker.io) for
configuration options.

1. Fork this repository
1. (Optional) edit `./kustomization.yml`
1. (Optional) add configuration to `./overlays/config/files/`
1. (Optional) add `./components` and `./overlays`

## Configuration

Java services load default configuration from `/opt/<service>/config/<service>.yml`
and custom configuration from `/opt/spinnaker/config/<service>.yml`.

Add your configuration to files like: `./overlays/config/files/clouddriver-local.yml`.

Kustomize will add this file to the `clouddriver` ConfigMap and mount into the
container at `/opt/spinnaker/config/clouddriver-local.yml`.

Java services use the Spring Boot framework which supports configuration in
[common application properties](https://docs.spring.io/spring-boot/docs/2.4.13/reference/html/appendix-application-properties.html#common-application-properties).

Configuration sources merge per Spring Boot [external configuration](https://docs.spring.io/spring-boot/docs/2.4.13/reference/html/spring-boot-features.html#boot-features-external-config).

Spring Boot links are subject to change as Spinnaker upgrades Spring Boot.
See: [Spinnaker Dependency Versions](https://github.com/spinnaker/kork/blob/master/spinnaker-dependencies/spinnaker-dependencies.gradle)

## Secrets

This Kustomize installer does not manage secrets, see:
[Spinnaker Secret Engines](https://spinnaker.io/docs/reference/halyard/secrets/#non-halyard-configuration).

## Working with Kustomize

See the [official Kustomize documentation](https://kubectl.docs.kubernetes.io/references/kustomize/).

### Kustomize Components

The Java services support [Spring Application Properties - Wildcard Locations](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.files.wildcard-locations).
This means that custom `components` or `overlays` can also mount files at
`/opt/spinnaker/config/<example>/<service>.yml`.

Where `<service>.yml` can be `{application}.yml` or `{application}-{profile}.yml`.

For example, adding MariaDB support to Clouddriver via a Kustomize component:

1. In the `./components/mariadb/` directory
1. `files/clouddriver.yml` contains Clouddriver SQL configuration
1. `kustomization.yml` generates a ConfigMap `clouddriver-mariadb` with the
   above file
1. Kustomize adds the ConfigMap to the Clouddriver Deployment Projected Volume
   sources, mounting the file at: `/opt/spinnaker/config/mariadb/clouddriver.yml`
1. The root `./kustomization.yml` includes this MariaDB component.

The quick start MariaDB and Redis components spawn a ConfigMap per component.
This convention enables the components to be standalone in this repository.

You can use this pattern to manage more than one Spinnaker installation with
Kustomize. Create a component/overlay called `dev` and another for `prod` and
put environment specific files there. Mount these at
`/opt/spinnaker/config/<dev|prod>/clouddriver.yml`. Note Spring's merge
behavior linked further up.

Separating configuration across files and ConfigMap's can make development
and troubleshooting difficult so try to put configuration directly into a
single file where possible, such as `clouddriver-local.yml`.

### Kustomize limitations

Kustomize supports appending files to a single `ConfigMap`. Kustomize cannot
append lines to a Kubernetes `ConfigMap` item, for example:

```
  data:
    clouddriver-example.yml | # << Kustomize can append items at this level
    clouddriver-local.yml: |
      # Some existing configuration

      # << Kustomize can't merge or append lines to clouddriver-local.yml
```

Some Spinnaker installations use custom Spring Profile's to load different
configuration per environment.

Unfortunately Kustomize `ValueAddTransformer` has limited functionality and
doesn't support appending to strings. For example, appending custom Spring
Profiles to each container's `SPRING_PROFILES_ACTIVE` environment variable.

Kustomize can replace strings so that could be suitable for some use cases.

## Contributing

Pull requests in line with the below goals are most welcome.

Goals:

1. Optimize for deployment of a basic Spinnaker installation with one command.
1. No Halyard, kleat or other tools for configuration. Use the services default
   configuration (eg: `clouddriver.yml`) and leverage Spring Profile's for
   customization (eg: `clouddriver-local.yml`).
1. Minimize duplication and maintainence. Define minimal configuration and
   patterns in this repository. See [spinnaker.io](https://spinnaker.io) Docs
   and service source code for all available options.

Please contribute documentation updates to https://github.com/spinnaker/spinnaker.io
