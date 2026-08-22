import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsDisableClusterStage = {
  provides: 'disableCluster',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsDisableClusterStage() {
  Registry.pipeline.registerStage(awsDisableClusterStage);
}

registerAwsDisableClusterStage();
