Spinnaker Cloud Provider Service
------------------------------------
[![Build Status](https://api.travis-ci.org/spinnaker/clouddriver.svg?branch=master)](https://travis-ci.org/spinnaker/clouddriver)

This service is the main integration point for Spinnaker cloud providers like AWS, GCE, CloudFoundry, Azure etc. 

### Developing with Intellij

To configure this repo as an Intellij project, run `./gradlew idea` in the root directory. 

Some of the modules make use of [Lombok](https://projectlombok.org/), which will compile correctly on its own. However, for Intellij to make sense of the Lombok annotations, you'll need to install the [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok-plugin) as well as [check 'enable' under annotation processing](https://www.jetbrains.com/help/idea/configuring-annotation-processing.html#3).
