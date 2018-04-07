# Kayenta
Automated Canary Analysis

[![Build Status](https://api.travis-ci.com/spinnaker/kayenta.svg?token=3dcx5xdA8twyS9T3VLnX&branch=master)](https://travis-ci.com/spinnaker/kayenta)

### Canary

Canary is a deployment process in which a change is partially rolled out, then
evaluated against the current deployment (baseline) to ensure that the new
deployment is operating at least as well as the old. This evaluation is done
using key metrics that are chosen when the canary is configured.

Canaries are usually run against deployments containing changes to code, but they
can also be used for operational changes, including changes to configuration.

The canary process is not a substitute for other forms of testing.

Kayenta is used by orca to enable automated canary deployments.

Please see the comprehensive [canary documentation](https://www.spinnaker.io/guides/user/canary/stage/) for more details.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8191.  The JVM will _not_ wait for the debugger
to be attached before starting Kayenta; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.

