'use strict';

import { WerckerTrigger } from './WerckerTrigger';
import { WerckerTriggerTemplate } from './WerckerTriggerTemplate';
import { ArtifactTypePatterns } from '../../../../artifact';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  component: WerckerTrigger,
  description: 'Listens to a Wercker pipeline',
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  key: 'wercker',
  label: 'Wercker',
  manualExecutionComponent: WerckerTriggerTemplate,
  validators: [
    {
      type: 'requiredField',
      fieldName: 'job',
      message: '<strong>pipeline</strong> is a required field on Wercker triggers.',
    },
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's Wercker trigger.
                You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
