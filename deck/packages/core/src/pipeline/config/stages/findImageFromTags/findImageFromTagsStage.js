'use strict';

import { module } from 'angular';

import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE =
  'spinnaker.core.pipeline.stage.findImageFromTagsStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE; // for backwards compatibility
export const findImageFromTagsStage = {
  useBaseProvider: true,
  key: 'findImageFromTags',
  label: 'Find Image from Tags',
  description: 'Finds an image to deploy from existing tags',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(findImageFromTagsStage);
module(CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE, []);
