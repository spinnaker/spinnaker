import { Registry } from 'core/registry';
import { NexusTrigger } from './NexusTrigger';
import { ArtifactTypePatterns, excludeAllTypesExcept } from 'core/artifact';

Registry.pipeline.registerTrigger({
  label: 'Nexus',
  description: 'Executes the pipeline on a Nexus repo update',
  key: 'nexus',
  component: NexusTrigger,
  validators: [],
  excludedArtifactTypePatterns: excludeAllTypesExcept(ArtifactTypePatterns.MAVEN_FILE),
});
