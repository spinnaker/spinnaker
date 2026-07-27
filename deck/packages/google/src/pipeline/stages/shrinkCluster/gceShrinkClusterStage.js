import { Registry } from '@spinnaker/core';

import { GceShrinkClusterStageConfig } from '../googleStageConfig';

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
