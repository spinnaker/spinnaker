import { Registry } from '@spinnaker/core';

import { DockerTriggerTemplate } from './DockerTriggerTemplate';
import { DockerTriggerConfig } from './DockerTriggerConfig';

Registry.pipeline.registerTrigger({
  label: 'Docker Registry',
  description: 'Executes the pipeline on an image update',
  key: 'docker',
  component: DockerTriggerConfig,
  manualExecutionComponent: DockerTriggerTemplate,
  validators: [
    {
      type: 'requiredField',
      fieldName: 'account',
      message: '<strong>Registry</strong> is a required field for Docker Registry triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'repository',
      message: '<strong>Image</strong> is a required field for Docker Registry triggers.',
    },
    {
      type: 'serviceAccountAccess',
      preventSave: true,
      message: `You do not have access to the service account configured in this pipeline's Docker Registry trigger.
                You will not be able to save your edits to this pipeline.`,
    },
  ],
});
