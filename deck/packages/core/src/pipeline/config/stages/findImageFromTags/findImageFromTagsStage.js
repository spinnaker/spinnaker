'use strict';

import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const findImageFromTagsStage = {
  useBaseProvider: true,
  key: 'findImageFromTags',
  label: 'Find Image from Tags',
  description: 'Finds an image to deploy from existing tags',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(findImageFromTagsStage);
