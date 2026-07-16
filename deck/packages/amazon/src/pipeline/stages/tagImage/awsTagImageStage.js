import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsTagImageStage = {
  provides: 'upsertImageTags',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  executionConfigSections: ['tagImageConfig', 'taskStatus'],
};

export function registerAwsTagImageStage() {
  Registry.pipeline.registerStage(awsTagImageStage);
}

registerAwsTagImageStage();
