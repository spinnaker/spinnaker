import { Registry } from '@spinnaker/core';

import { GceTargetServerGroupStageConfig } from '../googleStageConfig';

export function registerGceDisableAsgStage() {
  Registry.pipeline.registerStage({
    provides: 'disableServerGroup',
    cloudProvider: 'gce',
    component: GceTargetServerGroupStageConfig,
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
