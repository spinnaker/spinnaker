# spinnaker-gradle-project

[![Build Status](https://travis-ci.org/spinnaker/spinnaker-gradle-project.svg)](https://travis-ci.org/spinnaker/spinnaker-gradle-project)

Build conventions for spinnaker Gradle projects

## Usage

### Applying the Plugin

To include, add the following to your build.gradle

```groovy
plugins {
  id 'io.spinnaker.project' version "$spinnakerGradleVersion" apply false
}

allprojects {
  apply plugin: 'io.spinnaker.project'
}
```

### Extensions Provided

**spinnaker**

The spinnaker extension exposes dependency resolution utilities. By default the artifact
'com.netflix.spinnaker:spinnaker-dependencies:latest.release@yml' is resolved and used as
common dependency configuration (this can be overridden by setting `dependenciesYaml` or
`dependenciesVersion` on the spinnaker extension)

The dependency yaml format supports three sections:

- versions - a map of name to version string
- dependencies - a map of name to Gradle dependency notation, supporting Groovy simple templating
- groups - a map of name to a map of configuration name to a list of dependency names

Usage looks like:

```groovy
dependencies {
  spinnaker.group("bootWeb")
  compile spinnaker.dependency("bootActuator")
  compile "org.springframework:spring-context:${spinnaker.version('spring')}"
}
```

### Overriding resolved dependencies

Values from the `ExtraPropertiesExtension` override values read from the `dependenciesYaml`.

For example this:

```
ext {
  versions = [
    kork : '1.70.0'
  ]
}
```

Would pin the version of the `kork` library to `1.70.0` regardless of what version is defined
in the dependency file.
