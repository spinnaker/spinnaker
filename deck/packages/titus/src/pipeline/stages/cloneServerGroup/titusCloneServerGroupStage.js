import { Registry } from '@spinnaker/core';

import { TitusCloneServerGroupStageConfig } from '../common/TitusStageConfigs';

export const titusCloneServerGroupStage = {
  provides: 'cloneServerGroup',
  cloudProvider: 'titus',
  component: TitusCloneServerGroupStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusCloneServerGroupStage);
