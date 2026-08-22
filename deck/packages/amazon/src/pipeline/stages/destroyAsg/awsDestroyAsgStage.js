import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsDestroyAsgStage = {
  provides: 'destroyServerGroup',
  alias: 'destroyAsg',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsDestroyAsgStage() {
  Registry.pipeline.registerStage(awsDestroyAsgStage);
}

registerAwsDestroyAsgStage();
