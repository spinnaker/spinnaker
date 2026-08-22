import { Registry } from '@spinnaker/core';

import { TitusDestroyAsgStageConfig } from '../common/TitusStageConfigs';

export const titusDestroyAsgStage = {
  provides: 'destroyServerGroup',
  alias: 'destroyAsg',
  cloudProvider: 'titus',
  component: TitusDestroyAsgStageConfig,
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusDestroyAsgStage);
