import { Registry } from '@spinnaker/core';

import { AwsFindImageFromTagsStageConfig } from './AwsFindImageFromTagsStageConfig';

export const awsFindImageFromTagsStage = {
  key: 'findImageFromTags',
  provides: 'findImageFromTags',
  cloudProvider: 'aws',
  component: AwsFindImageFromTagsStageConfig,
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
