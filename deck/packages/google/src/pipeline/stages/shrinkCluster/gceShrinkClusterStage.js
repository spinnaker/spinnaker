import { Registry } from '@spinnaker/core';

import { GceShrinkClusterStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_SHRINKCLUSTER_GCESHRINKCLUSTERSTAGE =
  'spinnaker.gce.pipeline.stage..shrinkClusterStage';
export const name = GOOGLE_PIPELINE_STAGES_SHRINKCLUSTER_GCESHRINKCLUSTERSTAGE;
export function registerGceShrinkClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'shrinkCluster',
    cloudProvider: 'gce',
    component: GceShrinkClusterStageConfig,
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
