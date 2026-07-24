import { BakeExecutionLabel, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { ProxmoxBakeStageConfig } from './ProxmoxBakeStageConfig';

Registry.pipeline.registerStage({
  provides: 'bake',
  key: 'bake',
  cloudProvider: 'proxmox',
  label: 'Bake',
  description: 'Bakes a Proxmox template from a base template via rosco/packer.',
  component: ProxmoxBakeStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  executionLabelComponent: BakeExecutionLabel,
  supportsCustomTimeout: true,
  restartable: true,
  validators: [
    { type: 'requiredField', fieldName: 'region', fieldLabel: 'node' },
    { type: 'requiredField', fieldName: 'baseOs' },
  ],
});
