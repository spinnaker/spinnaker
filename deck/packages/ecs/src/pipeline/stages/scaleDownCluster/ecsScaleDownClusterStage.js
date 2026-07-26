import { Registry } from '@spinnaker/core';

import { EcsScaleDownClusterStageConfig } from '../common/EcsStageConfigs';

export function registerEcsScaleDownClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'scaleDownCluster',
    cloudProvider: 'ecs',
    component: EcsScaleDownClusterStageConfig,
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
