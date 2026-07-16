import { Registry } from '@spinnaker/core';

import { TitusEnableAsgStageConfig } from '../common/TitusStageConfigs';

export const titusEnableAsgStage = {
  provides: 'enableServerGroup',
  alias: 'enableAsg',
  cloudProvider: 'titus',
  component: TitusEnableAsgStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusEnableAsgStage);
