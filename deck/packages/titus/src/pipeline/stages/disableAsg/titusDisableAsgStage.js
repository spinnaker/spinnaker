import { Registry } from '@spinnaker/core';

import { TitusDisableAsgStageConfig } from '../common/TitusStageConfigs';

export const titusDisableAsgStage = {
  provides: 'disableServerGroup',
  alias: 'disableAsg',
  cloudProvider: 'titus',
  component: TitusDisableAsgStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusDisableAsgStage);
