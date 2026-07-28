import { Registry } from '@spinnaker/core';

import { GceDisableClusterStageConfig } from '../googleStageConfig';

export function registerGceDisableClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'disableCluster',
    cloudProvider: 'gce',
    component: GceDisableClusterStageConfig,
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
