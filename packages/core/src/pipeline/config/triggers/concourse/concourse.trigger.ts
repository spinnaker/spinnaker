import { ConcourseTrigger } from './ConcourseTrigger';
import { ConcourseTriggerTemplate } from './ConcourseTriggerTemplate';
import { ArtifactTypePatterns } from '../../../../artifact';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  label: 'Concourse',
  description: 'Listens to a Concourse job',
  key: 'concourse',
  component: ConcourseTrigger,
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
