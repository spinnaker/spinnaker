import { NexusTrigger } from './NexusTrigger';
import { ArtifactTypePatterns, excludeAllTypesExcept } from '../../../../artifact';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  label: 'Nexus',
  description: 'Executes the pipeline on a Nexus repo update',
  key: 'nexus',
  component: NexusTrigger,
  validators: [],
  excludedArtifactTypePatterns: excludeAllTypesExcept(ArtifactTypePatterns.MAVEN_FILE),
});
