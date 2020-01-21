## Gradle plugin for supporting spinnaker plugin implementations.

This **gradle** plugin allows Spinnaker developers to bundle, publish and register **_"spinnaker plugins"_** with spinnaker.

### Usage

```groovy
plugins {
   id 'com.netflix.spinnaker.gradle.extension.spinnakerextension'
}
```

### Notes

* Expects multi-project gradle builds where each sub project implements extensions targeting a single spinnaker service.
* Storage for plugin artifacts(bundled zip) can be like S3, GCS, jCenter or artifactory etc. ????


- [x] Compute Checksum for each artifact
- [x] Bundle up plugin artifacts into a single ZIP
- [ ] Deck artifacts zip with in the module and collect the same in the plugin bundle ?
- [ ] Publish bundle to ??
- [ ] How to register it with spinnaker ??
