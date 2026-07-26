'use strict';

import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const tagImageStage = {
  useBaseProvider: true,
  key: 'upsertImageTags',
  label: 'Tag Image',
  description: 'Tags an image',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(tagImageStage);
