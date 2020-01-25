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
  systemRequirements = "orca>=7.0.0" // Will default to `serviceName>=0.0.0` if undefined
}
```

#### Deck Module

```groovy
apply plugin: "io.spinnaker.plugin.ui-extension"
```

### Notes

* Expects multi-project gradle builds where each sub project implements extensions targeting a single spinnaker service.
* Storage for plugin artifacts(bundled zip) can be like S3, GCS, jCenter or artifactory etc. ????


- [x] Compute Checksum for each artifact
- [x] Bundle up plugin artifacts into a single ZIP
- [ ] Deck artifacts zip with in the module and collect the same in the plugin bundle ?
- [ ] Publish bundle to ??
- [ ] How to register it with spinnaker ??
