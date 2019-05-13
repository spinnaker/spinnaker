import { Registry } from 'core/registry';
import { ArtifactoryTrigger } from './ArtifactoryTrigger';
import { ArtifactTypePatterns, excludeAllTypesExcept } from 'core/artifact';

Registry.pipeline.registerTrigger({
  label: 'Artifactory',
  description: 'Executes the pipeline on an Artifactory repo update',
  key: 'artifactory',
  component: ArtifactoryTrigger,
  validators: [],
  excludedArtifactTypePatterns: excludeAllTypesExcept(ArtifactTypePatterns.MAVEN_FILE),
});
