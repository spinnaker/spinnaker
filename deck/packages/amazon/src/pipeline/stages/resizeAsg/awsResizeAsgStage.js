import { Registry } from '@spinnaker/core';

import { AwsResizeAsgStageConfig, validateAwsResizeAsgStage } from './AwsResizeAsgStageConfig';

export const awsResizeAsgStage = {
  key: 'resizeServerGroup',
  provides: 'resizeServerGroup',
  alias: 'resizeAsg',
  cloudProvider: 'aws',
  component: AwsResizeAsgStageConfig,
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
    {
      type: 'custom',
      preventSave: true,
      validate: (_pipeline, stage) => validateAwsResizeAsgStage(stage),
    },
  ],
};

export function registerAwsResizeAsgStage() {
  Registry.pipeline.registerStage(awsResizeAsgStage);
}

registerAwsResizeAsgStage();
