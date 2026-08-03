import { Registry } from '@spinnaker/core';

import { EcsDisableClusterStageConfig } from '../common/EcsStageConfigs';

export function registerEcsDisableClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'disableCluster',
    cloudProvider: 'ecs',
    component: EcsDisableClusterStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      {
        type: 'requiredField',
        fieldName: 'remainingEnabledServerGroups',
        fieldLabel: 'Keep [X] enabled Server Groups',
      },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
