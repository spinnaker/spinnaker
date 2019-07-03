local pipelines = import '../pipeline.libsonnet';

local artifact = pipelines.artifacts.front50PipelineTemplate()
.withReference('spinnaker://my-new-mpt');

pipelines.pipeline()
.withApplication('waze')
.withId('pipelines-demo-pipeline')
.withName('Demo pipeline')
.withTemplate(artifact)
.withSchema('v2')
.withInherit([])
.withNotifications([])
.withTriggers([])
.withVariableValues({ waitTime: 66 })
