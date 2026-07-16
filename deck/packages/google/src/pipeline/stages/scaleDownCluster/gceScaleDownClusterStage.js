import { Registry } from '@spinnaker/core';

import { GceScaleDownClusterStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE =
  'spinnaker.gce.pipeline.stage..scaleDownClusterStage';
export const name = GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE;
export function registerGceScaleDownClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'scaleDownCluster',
    cloudProvider: 'gce',
    component: GceScaleDownClusterStageConfig,
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      {
        type: 'requiredField',
        fieldName: 'remainingFullSizeServerGroups',
        fieldLabel: 'Keep [X] full size Server Groups',
      },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
    strategy: true,
  });
}
