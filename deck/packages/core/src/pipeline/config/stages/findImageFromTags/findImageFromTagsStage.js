'use strict';

import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE =
  'spinnaker.core.pipeline.stage.findImageFromTagsStage';
export const findImageFromTagsStage = {
  useBaseProvider: true,
  key: 'findImageFromTags',
  label: 'Find Image from Tags',
  description: 'Finds an image to deploy from existing tags',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(findImageFromTagsStage);
