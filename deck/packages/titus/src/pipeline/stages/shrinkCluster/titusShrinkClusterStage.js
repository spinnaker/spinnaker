import { Registry } from '@spinnaker/core';

import { TitusShrinkClusterStageConfig } from '../common/TitusStageConfigs';

export const titusShrinkClusterStage = {
  provides: 'shrinkCluster',
  cloudProvider: 'titus',
  component: TitusShrinkClusterStageConfig,
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusShrinkClusterStage);
