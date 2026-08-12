import { Registry } from '@spinnaker/core';

import { ModifyWarmPoolStageConfig } from './ModifyWarmPoolStageConfig';

export const awsModifyWarmPoolStage = {
  label: 'Modify Warm Pool',
  description: 'Upsert or delete an Auto Scaling Group warm pool',
  key: 'modifyWarmPool',
  component: ModifyWarmPoolStageConfig,
  executionConfigSections: ['modifyWarmPoolConfig', 'taskStatus'],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'action' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  cloudProvider: 'aws',
  strategy: true,
};

export function registerAwsModifyWarmPoolStage() {
  Registry.pipeline.registerStage(awsModifyWarmPoolStage);
}

registerAwsModifyWarmPoolStage();
