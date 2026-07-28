import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsDisableAsgStage = {
  provides: 'disableServerGroup',
  alias: 'disableAsg',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
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

export function registerAwsDisableAsgStage() {
  Registry.pipeline.registerStage(awsDisableAsgStage);
}

registerAwsDisableAsgStage();
