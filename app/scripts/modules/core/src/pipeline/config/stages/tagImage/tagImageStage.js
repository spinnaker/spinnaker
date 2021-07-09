'use strict';

import { module } from 'angular';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE = 'spinnaker.core.pipeline.stage.tagImageStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE, []).config(function () {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'upsertImageTags',
    label: 'Tag Image',
    description: 'Tags an image',
  });
});
