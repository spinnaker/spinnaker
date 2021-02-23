import { ArtifactTypePatterns } from 'core/artifact';
import { Registry } from 'core/registry';

import { PubsubTrigger } from './PubsubTrigger';

Registry.pipeline.registerTrigger({
  component: PubsubTrigger,
  description: 'Executes the pipeline when a pubsub message is received',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  key: 'pubsub',
  label: 'Pub/Sub',
  validators: [],
});
