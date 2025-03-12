Rosco
=====
[![Build Status](https://api.travis-ci.org/spinnaker/rosco.svg?branch=master)](https://travis-ci.org/spinnaker/rosco)

Rosco is Spinnaker's bakery, producing machine images with Hashicorp Packer and rendered manifests with templating engines Helm and Kustomize.

It presently supports producing Alibaba Cloud images, Google Compute Engine images, Huawei Cloud images, Tencent Cloud images, AWS AMI's and Azure images. It relies on [Hashicorp Packer](https://www.packer.io/) and can be easily extended to support additional platforms.

It exposes a REST api which can be experimented with via the Swagger UI: http://localhost:8087/swagger-ui.html

# Developing rosco

Need to run rosco locally for development? Here's what you need to setup and run:

## Environment Setup
```
git clone git@github.com:spinnaker/rosco.git
git clone git@github.com:spinnaker/spinnaker.git
```

## Docker Setup (runs redis locally)
```
docker-machine create --virtualbox-disk-size 8192 --virtualbox-memory 8192 -d virtualbox spinnaker
eval $(docker-machine env spinnaker)
cd spinnaker/experimental/docker-compose
docker-compose up -d redis
```

## Verify redis
```
docker run -it --link redis:redis --rm redis redis-cli -h redis -p 6379
(printf "PING\r\n";) | nc -v localhost 6379
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
./gradlew
```

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
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
docker-compose stop
docker-machine rm spinnaker
```
