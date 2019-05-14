import { ArtifactTypePatterns } from 'core/artifact';
import { Registry } from 'core/registry';

import { TravisTrigger } from './TravisTrigger';
import { TravisTriggerTemplate } from './TravisTriggerTemplate';

Registry.pipeline.registerTrigger({
  component: TravisTrigger,
  description: 'Listens to a Travis job',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  key: 'travis',
  label: 'Travis',
  manualExecutionComponent: TravisTriggerTemplate,
  providesVersionForBake: true,
  validators: [
    {
      type: 'requiredField',
      fieldName: 'job',
      message: '<strong>Job</strong> is a required field on Travis triggers.',
    },
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's Travis trigger.
                You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
