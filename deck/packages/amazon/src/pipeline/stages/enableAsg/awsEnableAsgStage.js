import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsEnableAsgStage = {
  provides: 'enableServerGroup',
  alias: 'enableAsg',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsEnableAsgStage() {
  Registry.pipeline.registerStage(awsEnableAsgStage);
}

registerAwsEnableAsgStage();
