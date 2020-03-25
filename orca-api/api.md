# Module orca-api

Contains all public Java APIs for Orca.

## Package com.netflix.spinnaker.orca.api.simplestage

The `SimpleStage` extension point is useful for the most basic custom stages that do not need to interact with separate systems.

## Package com.netflix.spinnaker.orca.api.pipeline.graph

The `StageDefinitionBuilder` extension point provides absolute low-level access to building a stage within Spinnaker.
It offers the ability to create arbitrarily complex stage definitions of any number of Tasks or the composition of other stages.

## Package com.netflix.spinnaker.orca.api.preconfigured.jobs

The `PreconfiguredJobConfigurationProvider` extension point provides a simple API for providing one or more preconfigured job stages.
A preconfigured job is a container that is executed as a stage and can be configured with a custom UI.
