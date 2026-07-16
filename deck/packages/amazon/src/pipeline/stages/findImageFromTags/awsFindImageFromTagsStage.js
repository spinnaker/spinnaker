import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsFindImageFromTagsStage = {
  provides: 'findImageFromTags',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  executionConfigSections: ['findImageConfig', 'taskStatus'],
  validators: [
    { type: 'requiredField', fieldName: 'packageName' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'tags' },
  ],
};

export function registerAwsFindImageFromTagsStage() {
  Registry.pipeline.registerStage(awsFindImageFromTagsStage);
}

registerAwsFindImageFromTagsStage();
