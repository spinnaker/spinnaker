# Spinnaker Extensions

This project provides a set of Gradle plugins to support developers in bundling, publishing, and registering **_
Spinnaker Plugins_** with Spinnaker.

## Table of Contents

- [Plugins](#plugins)
  - [Bundler](#bundler-plugin)
  - [UI Extension](#ui-extension-plugin)
  - [Service Extension](#service-extension-plugin)
  - [Compatibility Test Runner](#compatibility-test-runner)
- [Project Structure](#project-structure)
- [TODOS](#todos)

## <a name="plugins"></a>Plugins

### <a name="bundler-plugin"></a>Bundler

The bundler plugin defines a **_Spinnaker Plugin_** and registers tasks to group and bundle related extensions.

The bundle artifact can be stored wherever Spinnaker can access it for initiation. E.g. S3, GCS, artifactory, etc.

#### Tasks

| Task                | Description                                                                                                         | Dependencies                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| `collectPluginZips` | Copies the assembled extension zips into `build/distributions` directory of the bundle                              | `:<subProj(s)>:assemblePlugin` |
| `bundlePlugins`     | Zips all the extension zips together into one zip.                                                                  | `collectPluginZips`            |
| `checksumBundle`    | Produces a checksum for the bundle                                                                                  | `bundlePlugins`                |
| `releaseBundle`     | Makes sure that the bundle has been assembled and zipped and creates the `plugin-info.json` to describe the plugin. | `checksumBundle`               |

#### Project Extensions

| Extension         | Class                                                                                                                                                                                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `spinnakerBundle` | [SpinnakerBundleExtension](https://github.com/spinnaker/spinnaker-gradle-project/blob/68fd1accc037270e387fa889c394c32c90192289/spinnaker-extensions/src/main/kotlin/com/netflix/spinnaker/gradle/extension/extensions/SpinnakerBundleExtension.kt) |

#### How to apply

`apply plugin: "io.spinnaker.plugin.bundler"`

#### Example

```groovy
apply plugin: "io.spinnaker.plugin.bundler"

spinnakerBundle {
  pluginId = "Example.Plugin"
  description = "An example plugin description."
  provider = "https://github.com/some-example-repo"
  version = rootProject.version
}
```

### <a name="ui-extension-plugin"></a>UI Extension

The UI extension plugin registers tasks that will assemble a [Deck](https://github.com/spinnaker/deck) extension

#### Tasks

| Task             | Description                          | Dependencies  |
| ---------------- | ------------------------------------ | ------------- |
| `yarn`           | Installs package dependencies        |               |
| `yarnModules`    | Runs the command `yarn modules`      |               |
| `yarnBuild`      | Runs the command `yarn build`        | `yarnModules` |
| `assemblePlugin` | Zips the distribution files together | `build`       |

_Note_: Adds a dependency to the built in `build` task of `yarnBuild`

#### How to apply

`apply plugin: "io.spinnaker.plugin.ui-extension"`

#### Example

```groovy
apply plugin: "io.spinnaker.plugin.ui-extension"
```

### <a name="service-extension-plugin"></a>Service Extension

The Service extension plugin registers tasks that assemble a backend service extension

#### Tasks

| Task                      | Description                                                               | Dependencies |
| ------------------------- | ------------------------------------------------------------------------- | ------------ |
| `assemblePlugin`          | Zips plugin related files (dependency jars, class files, etc)             | `jar`        |
| `addPluginDataToManifest` | Gathers plugin details from extensions and adds them to the manifest file |              |

#### Project Extensions

| Extension         | Class                                                                                                                                                                                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `spinnakerPlugin` | [SpinnakerPluginExtension](https://github.com/spinnaker/spinnaker-gradle-project/blob/68fd1accc037270e387fa889c394c32c90192289/spinnaker-extensions/src/main/kotlin/com/netflix/spinnaker/gradle/extension/extensions/SpinnakerPluginExtension.kt) |

#### How to apply

`apply plugin: "io.spinnaker.plugin.service-extension"`

#### Example

```groovy
apply plugin: "java"
apply plugin: "io.spinnaker.plugin.service-extension"

repositories {
  mavenCentral()
}

dependencies {
  annotationProcessor "org.pf4j:pf4j:3.2.0"
  compileOnly("io.spinnaker.kork:kork-plugins-api:$korkVersion")
}

spinnakerPlugin {
  serviceName = "orca"
  pluginClassName = "com.example.service.ExamplePlugin"
  requires = "orca>=7.0.0" // Will default to `serviceName>=0.0.0` if undefined
}
```

### <a name="compatibility-test-runner"></a>Compatibility Test Runner

The Compatibility Test Runner plugin accompanies the Service Extension plugin to create tasks for easily testing your
plugins inside a set of top-level Spinnaker versions (e.g., `1.21.1`).

The test runner dynamically creates Gradle source sets for each top-level Spinnaker version you declare and re-writes
your test dependencies to depend on the service Gradle platform version (e.g., `io.spinnaker.orca:orca-bom`)
that corresponds to those Spinnaker versions. It does _not_ alter your plugin's compile or runtime classpaths. Your
plugin compiles against version `X`; the test runner runs your plugin inside Spinnaker `Y` and `Z`.

#### Tasks

| Task                                                  | Description                                                                    | Dependencies                                          |
| ----------------------------------------------------- | ------------------------------------------------------------------------------ | ----------------------------------------------------- |
| `compatibilityTest-${project.name}-${config.version}` | Tasks are created for each version specified in the bundle compatibility block |                                                       |
| `compatibilityTest`                                   | Created in the bundle project to run all extension compatibility tests         | `compatibilityTest-${project.name}-${config.version}` |

#### Project Extensions

| Extension       | Class                                                                                                                                                                                                                                                            |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compatibility` | [SpinnakerCompatibilityExtension](https://github.com/spinnaker/spinnaker-gradle-project/blob/68fd1accc037270e387fa889c394c32c90192289/spinnaker-extensions/src/main/kotlin/com/netflix/spinnaker/gradle/extension/extensions/SpinnakerCompatibilityExtension.kt) |

#### How to apply

- In the bundle project, add a `compatibility` block to the `spinnakerBundle` extension.
- In a service extension subproject, `apply plugin: "io.spinnaker.plugin.compatibility-test-runner"`

_Note_: Instead of providing an explicit version, you can also supply any of the following aliases:

- `latest`: the most recent Spinnaker release
- `supported`: the last three Spinnaker releases
- `nightly`: Spinnaker's nightly build

#### Example

```groovy
// bundle project
spinnakerBundle {
  // ...
  compatibility {
    spinnaker = ["1.21.1", "1.22.0"] // Set of Spinnaker versions to test against.
  }
}

// service extension subproject
apply plugin: "io.spinnaker.plugin.compatibility-test-runner"
```

#### Writing Tests

Tests should rely on Spinnaker's exported Gradle platforms (`<service>-bom`). This allows the `compatibility-test-runner`
to orchestrate tests against multiple versions of Spinnaker.

**_Example_**

```groovy
dependencies {
  testImplementation(platform("io.spinnaker.orca:orca-bom:${orcaVersion}"))
  // ...
  testImplementation("io.spinnaker.orca:orca-api-tck")
}
```

**_Recommendations_**:

- It is recommended to write tests in a Spring Boot - style integration test using [service test fixtures](https://github.com/spinnaker/orca/blob/master/orca-api-tck/src/main/kotlin/com/netflix/spinnaker/orca/api/test/OrcaFixture.kt).
  An example of this can be seen in [spinnaker-plugin-examples: RandomWaitStageIntegrationTest](https://github.com/spinnaker-plugin-examples/pf4jStagePlugin/blob/master/random-wait-orca/src/test/kotlin/io/armory/plugin/stage/wait/random/RandomWaitStageIntegrationTest.kt)

## <a name="project-structure"></a>Project structure

Although the gradle plugins are applied to individual projects they do require a particular project structure.

### Definitions

- **_bundle project_**, a project with the [bundle plugin](#bundler-plugin) applied
- **_extension project_**, a project with either the [UI extension](#ui-extension-plugin) or [Service extension](#service-extension-plugin) plugin applied.

### Rules

1. A parent **_bundle project_** is created with a `spinnakerBundle` extension applied
2. One or more **_extension projects_** are direct subprojects of the parent **_bundle project_**
3. Only one [UI extension](#ui-extension-plugin) can be under a **_bundle project_**
4. Only one [Service extension](#service-extension-plugin) can exist per service name
5. A **_bundle project_** can _NOT_ be nested under another **_bundle project_**
6. Multiple **_bundle projects_** are supported (Monorepo) as long as they don't break rule 3.

### <a name="todos"></a>TODOs

- [ ] Deck artifacts zip with in the module and collect the same in the plugin bundle ?
- [ ] Publish bundle to ??
- [ ] How to register it with spinnaker ??
