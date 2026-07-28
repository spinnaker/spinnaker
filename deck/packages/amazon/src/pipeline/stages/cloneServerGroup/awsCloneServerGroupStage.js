import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsCloneServerGroupStage = {
  provides: 'cloneServerGroup',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  accountExtractor: (stage) => [stage.context.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsCloneServerGroupStage() {
  Registry.pipeline.registerStage(awsCloneServerGroupStage);
}

registerAwsCloneServerGroupStage();
