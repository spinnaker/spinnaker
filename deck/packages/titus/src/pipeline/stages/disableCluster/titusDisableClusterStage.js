import { Registry } from '@spinnaker/core';

import { TitusDisableClusterStageConfig } from '../common/TitusStageConfigs';

export const titusDisableClusterStage = {
  provides: 'disableCluster',
  cloudProvider: 'titus',
  component: TitusDisableClusterStageConfig,
  executionConfigSections: ['disableClusterConfig', 'taskStatus'],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusDisableClusterStage);
