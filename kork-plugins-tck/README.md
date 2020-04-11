# kork-plugins-tck

This can be used to build JAR or Zip plugins for testing plugin and extension
point integration in services.  It also provides a standard set of tests that will run when tests
extend [PluginsTck](/src/main/kotlin/com/netflix/spinnaker/kork/plugins/tck/PluginsTck.kt) and
implement the [PluginsTckFixture](/src/main/kotlin/com/netflix/spinnaker/kork/plugins/tck/PluginsTckFixture.kt)

For example, see this implementation in Orca:

https://github.com/spinnaker/orca/orca-plugins-test
