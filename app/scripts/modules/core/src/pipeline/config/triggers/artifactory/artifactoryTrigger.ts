import { ArtifactoryTriggerConfig } from './ArtifactoryTriggerConfig';
import { Registry } from '@spinnaker/core';

Registry.pipeline.registerTrigger({
  label: 'Artifactory',
  description: 'Executes the pipeline on an Artifactory repo update',
  key: 'artifactory',
  component: ArtifactoryTriggerConfig,
  validators: [],
});
