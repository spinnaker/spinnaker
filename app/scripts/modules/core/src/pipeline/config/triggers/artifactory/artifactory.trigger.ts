import { ArtifactoryTrigger } from './ArtifactoryTrigger';
import { ArtifactTypePatterns, excludeAllTypesExcept } from '../../../../artifact';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  label: 'Artifactory',
  description: 'Executes the pipeline on an Artifactory repo update',
  key: 'artifactory',
  component: ArtifactoryTrigger,
  validators: [],
  excludedArtifactTypePatterns: excludeAllTypesExcept(ArtifactTypePatterns.MAVEN_FILE),
});
