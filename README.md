Orca
====

![Orca Logo](http://i62.photobucket.com/albums/h100/dallastt/MEME/265899.jpg)

Orca is the orchestration engine for Spinnaker.
It is responsible for taking a pipeline or task definition and managing the stages and tasks, coordinating the other Spinnaker services.

Orca pipelines are composed of _stages_ which in turn are composed of _tasks_.
The tasks of a stage share a common context and can publish to a global context shared across the entire pipeline allowing multiple stages to co-ordinate.
For example a _bake_ stage publishes details of the image it creates which is then used by a _deploy_ stage.

Orca persists a running execution to Redis.
