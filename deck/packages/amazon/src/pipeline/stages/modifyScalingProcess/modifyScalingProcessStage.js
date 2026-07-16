import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsModifyScalingProcessStage = {
  label: 'Modify Scaling Process',
  description: 'Suspend/Resume Scaling Processes',
  key: 'modifyAwsScalingProcess',
  alias: 'modifyScalingProcess',
  component: AmazonStageConfig,
  executionConfigSections: ['modifyScalingProcessesConfig', 'taskStatus'],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'action' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'processes' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  cloudProvider: 'aws',
  strategy: true,
};

export function registerAwsModifyScalingProcessStage() {
  Registry.pipeline.registerStage(awsModifyScalingProcessStage);
}

registerAwsModifyScalingProcessStage();
