import { Registry } from 'core/registry';
import { ArtifactoryTriggerConfig } from './ArtifactoryTriggerConfig';

Registry.pipeline.registerTrigger({
  label: 'Artifactory',
  description: 'Executes the pipeline on an Artifactory repo update',
  key: 'artifactory',
  component: ArtifactoryTriggerConfig,
  validators: [],
});
