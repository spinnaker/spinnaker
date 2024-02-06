Kayenta
====
[![Build Status](https://travis-ci.org/spinnaker/kayenta.svg?branch=master)](https://travis-ci.org/spinnaker/kayenta)

Kayenta is a platform for Automated Canary Analysis (ACA). It is used by Spinnaker to enable automated canary deployments. Please see the comprehensive [canary documentation](https://www.spinnaker.io/guides/user/canary/stage/) for more details.

### Canary Release
A canary release is a technique to reduce the risk from deploying a new version of software into production. A new version of software, referred to as the canary, is deployed to a small subset of users alongside the stable running version. Traffic is split between these two versions such that a portion of incoming requests are diverted to the canary. This approach can quickly uncover any problems with the new version without impacting the majority of users.

The quality of the canary version is assessed by comparing key metrics that describe the behavior of the old and new versions. If there is significant degradation in these metrics, the canary is aborted and all of the traffic is routed to the stable version in an effort to minimize the impact of unexpected behavior.

Canaries are usually run against deployments containing changes to code, but they
can also be used for operational changes, including changes to configuration.

### Frequently Asked Questions
See the [FAQ](docs/faq.md).

### Creating Canary Config
See the [Canary Config Object model](docs/canary-config.md) for how a canary config is defined in [Markdown Syntax for Object Notation (MSON)](https://github.com/apiaryio/mson).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8191.  The JVM will _not_ wait for the debugger
to be attached before starting Kayenta; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.

### Running Standalone Kayenta Locally

You can run a standalone kayenta instance locally with `docker-compose`.

```
# Copy and edit example config accordingly
cp kayenta-web/config/kayenta.yml ~/.spinnaker/kayenta.yml

# Build/Start Kayenta
docker-compose up
```

You should then be able to access your local kayenta instance at http://localhost:8090/swagger-ui/index.html.
