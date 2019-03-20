import { Registry } from 'core/registry';
import { ArtifactoryTriggerConfig } from './ArtifactoryTriggerConfig';
import { ArtifactTypePatterns, excludeAllTypesExcept } from 'core/artifact';

Registry.pipeline.registerTrigger({
  label: 'Artifactory',
  description: 'Executes the pipeline on an Artifactory repo update',
  key: 'artifactory',
  component: ArtifactoryTriggerConfig,
  validators: [],
  excludedArtifactTypePatterns: excludeAllTypesExcept(ArtifactTypePatterns.MAVEN_FILE),
});
