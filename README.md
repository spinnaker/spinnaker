spinnaker-gradle-project
==============

Build conventions for spinnaker Gradle projects

## Usage

### Applying the Plugin

To include, add the following to your build.gradle

    buildscript {
      repositories { jcenter() }

      dependencies {
        classpath 'com.netflix.spinnaker:spinnaker-gradle-project:1.9.+'
      }
    }

    apply plugin: 'spinnaker-gradle-project'

### Tasks Provided

None

### Extensions Provided

**Dependencies**

```groovy
dependencies {
  spinnaker.group("bootWeb")
  compile spinnaker.dependency("bootActuator")
}
```

spinnaker#group will resolve a group of dependencies as defined in src/main/resources/dependencies.yml
spinnaker#dependency will resolve a specific named dependency as defined in src/main/resources/dependencies.yml

**IDEA Config**

```groovy
ideaConfig {
  mainClassName = 'com.netflix.spinnaker.myApp.Main'
  codeStyleXml = file('gradle/codeStyle.xml')
}
```
