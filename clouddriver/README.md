Spinnaker Cloud Provider Service
------------------------------------
[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml)

This service is the main integration point for Spinnaker cloud providers like AWS, GCE, CloudFoundry, Azure etc.
It lives in this monorepo at `clouddriver/`; commands below are run from the monorepo root, not
from within this directory — see [CLAUDE.md](../CLAUDE.md) for the full set of build/test commands.

### Developing with Intellij

To configure this repo as an Intellij project, run `./gradlew idea` from the monorepo root.

Some of the modules make use of [Lombok](https://projectlombok.org/), which will compile correctly on its own. However, for Intellij to make sense of the Lombok annotations, you'll need to install the [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok-plugin) as well as [check 'enable' under annotation processing](https://www.jetbrains.com/help/idea/configuring-annotation-processing.html#3).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):
```
./gradlew clouddriver -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7102.  The JVM will _not_ wait for
the debugger to be attached before starting Clouddriver; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
