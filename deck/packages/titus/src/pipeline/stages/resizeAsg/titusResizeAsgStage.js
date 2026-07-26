import { Registry } from '@spinnaker/core';

import { TitusResizeAsgStageConfig } from '../common/TitusStageConfigs';

export const titusResizeAsgStage = {
  provides: 'resizeServerGroup',
  alias: 'resizeAsg',
  cloudProvider: 'titus',
  component: TitusResizeAsgStageConfig,
  executionConfigSections: ['resizeServerGroupConfig', 'taskStatus'],
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'action' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

Registry.pipeline.registerStage(titusResizeAsgStage);
