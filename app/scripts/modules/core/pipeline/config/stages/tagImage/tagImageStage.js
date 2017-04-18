'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImageStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'upsertImageTags',
      label: 'Tag Image',
      description: 'Tags an image',
    });
  });
