import { Registry } from '@spinnaker/core';

import { GceDisableClusterStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE =
  'spinnaker.gce.pipeline.stage..disableClusterStage';
export const name = GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE;
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
