## Gradle plugin for supporting spinnaker plugin implementations.

This **gradle** plugin allows Spinnaker developers to bundle, publish and register **_"spinnaker plugins"_** with spinnaker.

### Usage

```groovy
buildscript {
  repositories {
    maven { url "https://dl.bintray.com/spinnaker/gradle/" }
  }
  dependencies {
    classpath("com.netflix.spinnaker.gradle:spinnaker-extensions:$spinnakerGradleVersion")
  }
}
```

* Root project must `apply plugin: "io.spinnaker.plugin.bundler`
* Deck extension module must `apply plugin: "io.spinnaker.plugin.ui-extension`
* Backend extension modules must `apply plugin: "io.spinnaker.plugin.service-extension`

#### Root Module

```groovy
apply plugin: "io.spinnaker.plugin.bundler"

spinnakerBundle {
  pluginId    = "com.netflix.streaming.platform.cde.aws-rds"
  description = "AWS RDS infrastructure management"
  version     = "1.0.0" // Will be inferred from git tags if undefined
  provider    = "https://github.com/Netflix"
}
```

#### Service Modules

```groovy
apply plugin: "java"
apply plugin: "io.spinnaker.plugin.service-extension"

repositories {
  jcenter()
  maven { url "http://dl.bintray.com/spinnaker/spinnaker/" }
}

dependencies {
  annotationProcessor "org.pf4j:pf4j:3.2.0"
  compileOnly("com.netflix.spinnaker.kork:kork-plugins-api:$korkVersion")
}

spinnakerPlugin {
  serviceName        = "orca"
  pluginClassName    = "com.netflix.streaming.platform.cde.AwsRdsPlugin"
  requires           = "orca>=7.0.0" // Will default to `serviceName>=0.0.0` if undefined
}
```

#### Deck Module

```groovy
apply plugin: "io.spinnaker.plugin.ui-extension"
```


#### Compatibility Test Runner

```groovy
// Inside root project
spinnakerBundle {
  // ...
  compatibility {
    spinnaker = ["1.21.1", "1.22.0"] // Set of Spinnaker versions to test against.
  }
}

// Inside service extension subproject
apply plugin: "io.spinnaker.plugin.compatibility-test-runner"
```

The compatibility test runner allows plugin developers to easily test their plugins inside a set of top-level Spinnaker versions (e.g., `1.21.1`).

The development flow looks something like this:
- Develop a plugin.
- Write tests, preferably Spring Boot-style integration tests using [service test fixtures](https://github.com/spinnaker/orca/blob/master/orca-api-tck/src/main/kotlin/com/netflix/spinnaker/orca/api/test/OrcaFixture.kt).
  You can find an example of this kind of test in our [Spinnaker plugin example repositories](https://github.com/spinnaker-plugin-examples/pf4jStagePlugin/blob/master/random-wait-orca/src/test/kotlin/io/armory/plugin/stage/wait/random/RandomWaitStageIntegrationTest.kt).
- Your integration tests should depend on Spinnaker's exported Gradle platforms, which take the form `<service>-bom`. For example, if your test relies on the Orca test fixture, your subproject build file should look something like this:
```groovy
  dependencies {
    // ...
    testImplementation("com.netflix.spinnaker.orca:orca-bom:<orca-version>")
    testImplementation("com.netflix.spinnaker.orca:orca-api-tck")
  }
```

- Install this Gradle plugin, and configure it to run tests against a set of top-level Spinnaker versions.
  The plugin will create a set of tasks: a set of `compatibilityTest-<project>-<spinnaker-version>` tasks, and a top-level
  `compatibilityTest` task that will run all of the compatibility test subtasks.

The test runner dynamically creates Gradle source sets for each top-level Spinnaker version you declare and re-writes your test dependencies to
depend on the service Gradle platform version (e.g., `com.spinnaker.netflix.orca:orca-bom`) that corresponds to those Spinnaker versions.
It does _not_ alter your plugin's compile or runtime classpaths. Your plugin compiles against version `X`; the test runner runs your plugin inside Spinnaker `Y` and `Z`.

Instead of providing an explicit version, you can also supply any of the following aliases:

- `latest`: the most recent Spinnaker release
- `supported`: the last three Spinnaker releases
- `nightly`: Spinnaker's nightly build

### Notes

* Expects multi-project gradle builds where each sub project implements extensions targeting a single spinnaker service.
* Storage for plugin artifacts(bundled zip) can be like S3, GCS, jCenter or artifactory etc. ????


- [x] Compute Checksum for each artifact
- [x] Bundle up plugin artifacts into a single ZIP
- [ ] Deck artifacts zip with in the module and collect the same in the plugin bundle ?
- [ ] Publish bundle to ??
- [ ] How to register it with spinnaker ??
