import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { ProxmoxResizeStageConfig } from './ProxmoxResizeStageConfig';
import { ProxmoxServerGroupStageConfig } from './ProxmoxServerGroupStageConfig';

const targetValidators = [
  { type: 'requiredField', fieldName: 'cluster' },
  { type: 'requiredField', fieldName: 'target' },
  { type: 'requiredField', fieldName: 'region', fieldLabel: 'node' },
  { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
];

Registry.pipeline.registerStage({
  provides: 'destroyServerGroup',
  key: 'destroyServerGroup',
  cloudProvider: 'proxmox',
  component: ProxmoxServerGroupStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
    },
    ...targetValidators,
  ],
});

Registry.pipeline.registerStage({
  provides: 'resizeServerGroup',
  key: 'resizeServerGroup',
  cloudProvider: 'proxmox',
  component: ProxmoxResizeStageConfig,
  validators: targetValidators,
});

Registry.pipeline.registerStage({
  label: 'Stop Server Group',
  description: 'Stops every VM in a Proxmox server group without deleting them.',
  key: 'stopProxmoxServerGroup',
  cloudProvider: 'proxmox',
  component: ProxmoxServerGroupStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  validators: targetValidators,
});

Registry.pipeline.registerStage({
  label: 'Start Server Group',
  description: 'Starts every VM in a Proxmox server group.',
  key: 'startProxmoxServerGroup',
  cloudProvider: 'proxmox',
  component: ProxmoxServerGroupStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  validators: targetValidators,
});
