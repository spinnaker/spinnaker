import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsRollbackClusterStage = {
  provides: 'rollbackCluster',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsRollbackClusterStage() {
  Registry.pipeline.registerStage(awsRollbackClusterStage);
}

registerAwsRollbackClusterStage();
