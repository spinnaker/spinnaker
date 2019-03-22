import { Registry } from 'core/registry';
import { ArtifactTypePatterns } from 'core/artifact';

import { ConcourseTriggerConfig } from './ConcourseTriggerConfig';
import { ConcourseTriggerTemplate } from './ConcourseTriggerTemplate';

Registry.pipeline.registerTrigger({
  label: 'Concourse',
  description: 'Listens to a Concourse job',
  key: 'concourse',
  component: ConcourseTriggerConfig,
  manualExecutionComponent: ConcourseTriggerTemplate,
  excludedArtifactTypePatterns: [ArtifactTypePatterns.JENKINS_FILE],
  validators: [
    {
      type: 'requiredField',
      fieldName: 'team',
      message: '<strong>Team</strong> is a required field on Concourse triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'project',
      message: '<strong>Pipeline</strong> is a required field on Concourse triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'jobName',
      message: '<strong>Job</strong> is a required field on Concourse triggers.',
    },
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's Jenkins trigger.
                    You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
