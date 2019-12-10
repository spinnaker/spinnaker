'use strict';

import { Registry } from 'core/registry';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE =
  'spinnaker.core.pipeline.stage.findImageFromTagsStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE, []).config(function() {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'findImageFromTags',
    label: 'Find Image from Tags',
    description: 'Finds an image to deploy from existing tags',
  });
});
