import { Registry } from '@spinnaker/core';

import { TitusScaleDownClusterStageConfig } from '../common/TitusStageConfigs';

export const titusScaleDownClusterStage = {
  provides: 'scaleDownCluster',
  cloudProvider: 'titus',
  component: TitusScaleDownClusterStageConfig,
  executionConfigSections: ['scaleDownClusterConfig', 'taskStatus'],
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
};

Registry.pipeline.registerStage(titusScaleDownClusterStage);
