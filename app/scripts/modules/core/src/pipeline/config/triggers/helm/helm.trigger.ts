import { HelmManualTriggerConfig } from './HelmManualTriggerConfig';
import { HelmTriggerConfig } from './HelmTriggerConfig';
import { HelmTriggerExecutionStatus } from './HelmTriggerExecutionStatus';
import { Registry } from '../../../../registry';

export const HELM_TRIGGER_TYPE = 'helm';

Registry.pipeline.registerTrigger({
  label: 'Helm Chart',
  description: 'Executes the pipeline on a Helm chart update',
  key: HELM_TRIGGER_TYPE,
  component: HelmTriggerConfig,
  executionStatusComponent: HelmTriggerExecutionStatus,
  manualExecutionComponent: HelmManualTriggerConfig,
  validators: [
    {
      type: 'requiredField',
      fieldName: 'account',
      message: '<strong>Account</strong> is a required field for Helm triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'chart',
      message: '<strong>Chart</strong> is a required field for Helm triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'version',
      message: '<strong>Version</strong> is a required field for Helm triggers.',
    },
    {
      type: 'serviceAccountAccess',
      preventSave: true,
      message: `You do not have access to the service account configured in this pipeline's Helm trigger.
                You will not be able to save your edits to this pipeline.`,
    },
  ],
});
