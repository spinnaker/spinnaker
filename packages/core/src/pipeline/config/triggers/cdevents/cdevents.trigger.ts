import { CDEventsTrigger } from './CDEventsTrigger';
import { ArtifactTypePatterns } from '../../../../artifact';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  component: CDEventsTrigger,
  description: 'Executes the pipeline when a CDEvents webhook is received.',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  key: 'cdevents',
  label: 'CDEvents',
  validators: [
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's CDEvents trigger.
                You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
