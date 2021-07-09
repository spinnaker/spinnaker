'use strict';

import { JenkinsTrigger } from './JenkinsTrigger';
import { JenkinsTriggerExecutionStatus } from './JenkinsTriggerExecutionStatus';
import { JenkinsTriggerTemplate } from './JenkinsTriggerTemplate';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  component: JenkinsTrigger,
  description: 'Listens to a Jenkins job',
  executionStatusComponent: JenkinsTriggerExecutionStatus,
  executionTriggerLabel: () => 'Triggered Build',
  key: 'jenkins',
  label: 'Jenkins',
  manualExecutionComponent: JenkinsTriggerTemplate,
  providesVersionForBake: true,
  validators: [
    {
      type: 'requiredField',
      fieldName: 'job',
      message: '<strong>Job</strong> is a required field on Jenkins triggers.',
    },
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's Jenkins trigger.
                You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
