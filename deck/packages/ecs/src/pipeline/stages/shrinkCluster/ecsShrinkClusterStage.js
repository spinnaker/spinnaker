import { Registry } from '@spinnaker/core';

import { EcsShrinkClusterStageConfig } from '../common/EcsStageConfigs';

export function registerEcsShrinkClusterStage() {
  Registry.pipeline.registerStage({
    provides: 'shrinkCluster',
    cloudProvider: 'ecs',
    component: EcsShrinkClusterStageConfig,
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
