import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsScaleDownClusterStage = {
  provides: 'scaleDownCluster',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    {
      type: 'requiredField',
      fieldName: 'remainingFullSizeServerGroups',
      fieldLabel: 'Keep [X] full size Server Groups',
    },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  strategy: true,
};

export function registerAwsScaleDownClusterStage() {
  Registry.pipeline.registerStage(awsScaleDownClusterStage);
}

registerAwsScaleDownClusterStage();
