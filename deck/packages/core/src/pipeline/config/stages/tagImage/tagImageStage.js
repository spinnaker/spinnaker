'use strict';

import { module } from 'angular';

import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE = 'spinnaker.core.pipeline.stage.tagImageStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE; // for backwards compatibility
export const tagImageStage = {
  useBaseProvider: true,
  key: 'upsertImageTags',
  label: 'Tag Image',
  description: 'Tags an image',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(tagImageStage);
module(CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE, []);
