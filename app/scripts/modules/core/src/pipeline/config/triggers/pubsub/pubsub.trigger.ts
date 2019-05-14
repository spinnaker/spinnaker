import { ArtifactTypePatterns } from 'core/artifact';
import { PubsubTrigger } from './PubsubTrigger';
import { Registry } from 'core/registry';

Registry.pipeline.registerTrigger({
  component: PubsubTrigger,
  description: 'Executes the pipeline when a pubsub message is received',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  key: 'pubsub',
  label: 'Pub/Sub',
  validators: [],
});
