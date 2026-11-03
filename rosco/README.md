Rosco
=====
[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/rosco.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/rosco.yml)

Rosco is Spinnaker's bakery, producing machine images with Hashicorp Packer and rendered manifests with templating engines Helm and Kustomize.

It presently supports producing Alibaba Cloud images, Google Compute Engine images, Huawei Cloud images, Tencent Cloud images, AWS AMI's and Azure images. It relies on [Hashicorp Packer](https://www.packer.io/) and can be easily extended to support additional platforms.

It exposes a REST api which can be experimented with via the Swagger UI: http://localhost:8087/swagger-ui.html

# Developing rosco

Need to run rosco locally for development? Rosco lives in this monorepo at `rosco/`; all
`./gradlew` commands below are run from the monorepo root, not from within this directory. See
[CLAUDE.md](../CLAUDE.md) for the full set of build/test/run commands.

Rosco needs a local redis instance, e.g.:
```
docker run -d -p 6379:6379 redis
```

## IDE setup

### Generate Intellij gradle project files
```
./gradlew idea
```

### Apply groovy code formatting scheme

1) Preferences -> Editor -> Code Style -> Manage ... -> Import -> select codestyle.xml from the project directory.
2) Apply the 'spinnaker' scheme.

## Running App
```
./gradlew rosco
```

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew rosco -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8187.  The JVM will _not_ wait for the debugger
to be attached before starting Rosco; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.

## Verifying
```
curl -v localhost:8087/bakeOptions
```

## Swagger
```
http://localhost:8087/swagger-ui.html
```

## Docker teardown
```
docker stop <redis-container-id>
```
